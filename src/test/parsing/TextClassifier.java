package test.parsing;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.SMO;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

public class TextClassifier {
    
    public static void main(String[] args) {
        try ( FileInputStream file = new FileInputStream(new File("partopar - labeled.xls"))) {
            HSSFWorkbook workbook = new HSSFWorkbook(file);
            Iterator<Row> rowIterator = workbook.getSheetAt(0).iterator();

            FastVector fvClassVal = new FastVector();
            fvClassVal.addElement("application status");
            fvClassVal.addElement("employment status");
            fvClassVal.addElement("explanation");
            fvClassVal.addElement("location rule");
            fvClassVal.addElement("nationality status");
            fvClassVal.addElement("restriction rule");
            fvClassVal.addElement("rights directive");
            Attribute classAttr = new Attribute("class", fvClassVal);
            
            FastVector attribs = new FastVector();
            for (int i = 0; i < 15; i++)
                attribs.addElement(new Attribute("attr" + (i+1)));
            attribs.addElement(classAttr);
            Instances traindata = new Instances("RelTrain", attribs, 10);
            traindata.setClassIndex(traindata.numAttributes()-1);
            Instances testdata = new Instances(traindata);
            testdata.setClassIndex(testdata.numAttributes()-1);
            
            while(rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Cell cell = row.getCell(0);
                if (cell != null &&  cell.getStringCellValue() != null ) {
                    String value = cell.getStringCellValue();
                    Instance inst = new DenseInstance(traindata.numAttributes());
                    inst.setValue(0, value.matches("(?is).*cannot.*") ? 1 : 0);
                    inst.setValue(1, value.matches("(?is).*impose.*") ? 1 : 0);
                    inst.setValue(2, value.matches("(?is).*must be noted.*") ? 1 : 0);
                    inst.setValue(3, value.matches("(?is).*in mind.*") ? 1 : 0);
                    inst.setValue(4, value.matches("(?is).*area.*") ? 1 : 0);
                    inst.setValue(5, value.matches("(?is).*grant.*") ? 1 : 0);
                    inst.setValue(6, value.matches("(?is).*right.*") ? 1 : 0);
                    inst.setValue(7, value.matches("(?is).*require.*") ? 1 : 0);
                    inst.setValue(8, value.matches("(?is).*employ.*") ? 1 : 0);
                    inst.setValue(9, value.matches("(?is).*recognize.*") ? 1 : 0);
                    inst.setValue(10, value.matches("(?is).*pointed out.*") ? 1 : 0);
                    inst.setValue(11, value.matches("(?is).*(nationals|nationality).*") ? 1 : 0);
                    inst.setValue(12, value.matches("(?is).*application.*") ? 1 : 0);
                    inst.setValue(13, value.matches("(?is).*permit.*") ? 1 : 0);
                    inst.setValue(14, value.matches("(?is).*geographic.*") ? 1 : 0);
                    String label = null;
                    Cell cell2 = row.getCell(2);
                    if (cell2 != null && cell2.getStringCellValue() != null)
                        label = cell2.getStringCellValue();
                    if (label != null) {
                        inst.setDataset(traindata);
                        inst.setValue(classAttr, label.trim());
                        traindata.add(inst);
                    } else {
                        inst.setDataset(testdata);
                        testdata.add(inst);
                    }  
                }
            }
            SMO classifier = new SMO(); 
            Evaluation eval = new Evaluation(testdata);
            eval.crossValidateModel(classifier, traindata, 5, new Random(1));
            System.out.println(eval.toSummaryString());
            System.out.println(eval.toMatrixString());
        } catch (FileNotFoundException ex) {
            Logger.getLogger(TextClassifier.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(TextClassifier.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(TextClassifier.class.getName()).log(Level.SEVERE, null, ex);
        } 
    }
}
