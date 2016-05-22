package org.hl7.fhir.definitions.parsers;

import java.util.HashMap;
import java.util.Map;

import org.hl7.fhir.dstu3.model.CodeSystem;
import org.hl7.fhir.dstu3.model.CodeSystem.CodeSystemContentMode;
import org.hl7.fhir.dstu3.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.hl7.fhir.dstu3.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.dstu3.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.dstu3.model.ValueSet.ValueSetComposeComponent;
import org.hl7.fhir.dstu3.terminologies.CodeSystemUtilities;
import org.hl7.fhir.dstu3.utils.ToolingExtensions;
import org.hl7.fhir.tools.converters.CodeSystemConvertor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.XLSXmlParser.Sheet;

public class CodeListToValueSetParser {

  private Sheet sheet;
  private ValueSet valueSet;
  private String version;
  private String sheetName;
  private TabDelimitedSpreadSheet tabfmt;
  private Map<String, CodeSystem> codeSystems;

  public CodeListToValueSetParser(Sheet sheet, String sheetName, ValueSet valueSet, String version, TabDelimitedSpreadSheet tabfmt, Map<String, CodeSystem> codeSystems) throws Exception {
    super();
    this.sheet = sheet;
    this.sheetName = sheetName;
    this.valueSet = valueSet;
    this.version = version;
    this.tabfmt = tabfmt;
    this.codeSystems = codeSystems;

    tabfmt.column("System");
    tabfmt.column("Id");
    tabfmt.column("Abstract");
    tabfmt.column("Code");
    tabfmt.column("Display");
    tabfmt.column("Definition");
    tabfmt.column("Comment");
    tabfmt.column("v2");
    tabfmt.column("v3");
    tabfmt.column("Parent");
    for (String ct : sheet.columns) 
      if (ct.startsWith("Display:"))
        tabfmt.column(ct);
  }

  public void execute() throws Exception {
    boolean hasDefine = false;
    for (int row = 0; row < sheet.rows.size(); row++) {
      tabfmt.row();
      tabfmt.cell(sheet.getColumn(row, "System"));
      tabfmt.cell(sheet.getColumn(row, "Id"));
      tabfmt.cell(sheet.getColumn(row, "Abstract"));
      tabfmt.cell(sheet.getColumn(row, "Code"));
      tabfmt.cell(sheet.getColumn(row, "Display"));
      tabfmt.cell(sheet.getColumn(row, "Definition"));
      tabfmt.cell(sheet.getColumn(row, "Comment"));
      tabfmt.cell(sheet.getColumn(row, "v2"));
      tabfmt.cell(sheet.getColumn(row, "v3"));
      tabfmt.cell(sheet.getColumn(row, "Parent"));
      for (String ct : sheet.columns) 
        if (ct.startsWith("Display:"))
          tabfmt.cell(sheet.getColumn(row, ct));

      hasDefine = hasDefine || Utilities.noString(sheet.getColumn(row, "System"));
    }

    Map<String, ConceptDefinitionComponent> codes = new HashMap<String, ConceptDefinitionComponent>();
    Map<String, ConceptDefinitionComponent> codesById = new HashMap<String, ConceptDefinitionComponent>();
    
    Map<String, ConceptSetComponent> includes = new HashMap<String, ConceptSetComponent>();

    
    if (hasDefine) {
      CodeSystem cs = new CodeSystem();
      cs.setUrl("http://hl7.org/fhir/"+sheetName);
      valueSet.getCompose().addInclude().setSystem(cs.getUrl());
      CodeSystemConvertor.populate(cs, valueSet);
      cs.setVersion(version);
      cs.setCaseSensitive(true);
      cs.setContent(CodeSystemContentMode.COMPLETE);
      codeSystems.put(cs.getUrl(), cs);

      for (int row = 0; row < sheet.rows.size(); row++) {
        if (Utilities.noString(sheet.getColumn(row, "System"))) {

          ConceptDefinitionComponent cc = new ConceptDefinitionComponent(); 
          cc.setUserData("id", sheet.getColumn(row, "Id"));
          cc.setCode(sheet.getColumn(row, "Code"));
          if (codes.containsKey(cc.getCode()))
            throw new Exception("Duplicate Code "+cc.getCode());
          codes.put(cc.getCode(), cc);
          codesById.put(cc.getUserString("id"), cc);
          cc.setDisplay(sheet.getColumn(row, "Display"));
          if (sheet.getColumn(row, "Abstract").toUpperCase().equals("Y"))
          	CodeSystemUtilities.setNotSelectable(cs, cc);
          if (cc.hasCode() && !cc.hasDisplay())
            cc.setDisplay(Utilities.humanize(cc.getCode()));
          cc.setDefinition(sheet.getColumn(row, "Definition"));
          if (!Utilities.noString(sheet.getColumn(row, "Comment")))
            ToolingExtensions.addComment(cc, sheet.getColumn(row, "Comment"));
          cc.setUserData("v2", sheet.getColumn(row, "v2"));
          cc.setUserData("v3", sheet.getColumn(row, "v3"));
          for (String ct : sheet.columns) 
            if (ct.startsWith("Display:") && !Utilities.noString(sheet.getColumn(row, ct)))
              cc.addDesignation().setLanguage(ct.substring(8)).setValue(sheet.getColumn(row, ct));
          String parent = sheet.getColumn(row, "Parent");
          if (Utilities.noString(parent))
            cs.addConcept(cc);
          else if (parent.startsWith("#") && codesById.containsKey(parent.substring(1)))
            codesById.get(parent.substring(1)).addConcept(cc);
          else if (codes.containsKey(parent))
            codes.get(parent).addConcept(cc);
          else
            throw new Exception("Parent "+parent+" not resolved in "+sheetName);
        }
      }
    }

    for (int row = 0; row < sheet.rows.size(); row++) {
      if (!Utilities.noString(sheet.getColumn(row, "System"))) {
        String system = sheet.getColumn(row, "System");
        ConceptSetComponent t = includes.get(system);
        if (t == null) {
          if (!valueSet.hasCompose())
            valueSet.setCompose(new ValueSetComposeComponent());
          t = valueSet.getCompose().addInclude();
          t.setSystem(system);
          includes.put(system, t);
        }
        ConceptReferenceComponent cc = t.addConcept();
        cc.setCode(sheet.getColumn(row, "Code"));
        if (codes.containsKey(cc.getCode()))
          throw new Exception("Duplicate Code "+cc.getCode());
        codes.put(cc.getCode(), null);
        cc.setDisplay(sheet.getColumn(row, "Display"));
        if (!Utilities.noString(sheet.getColumn(row, "Definition")))
          ToolingExtensions.addDefinition(cc, sheet.getColumn(row, "Definition"));
        if (!Utilities.noString(sheet.getColumn(row, "Comment")))
          ToolingExtensions.addComment(cc, sheet.getColumn(row, "Comment"));
        cc.setUserDataINN("v2", sheet.getColumn(row, "v2"));
        cc.setUserDataINN("v3", sheet.getColumn(row, "v3"));
        for (String ct : sheet.columns) 
          if (ct.startsWith("Display:") && !Utilities.noString(sheet.getColumn(row, ct)))
            cc.addDesignation().setLanguage(ct.substring(8)).setValue(sheet.getColumn(row, ct));       
      }
    }

  }


}
