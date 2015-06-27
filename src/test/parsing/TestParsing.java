package test.parsing;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.RandomAccessFileOrArray;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.SimpleTextExtractionStrategy;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 *
 * @author Paulius Danenas
 */
public class TestParsing {

    private static String case_dir = "D:\\copenhagen\\cases-95-2013";
    private static String url = "jdbc:mysql://localhost:3306/court_db";
    private static String username = "testing";
    private static String password = "testing";
    
    private static String getMatch(Pattern pattern, String string, int index) {
        Matcher m = pattern.matcher(string);
        m.find();
        try {
            if (m.group(index) != null)
                return WordUtils.capitalizeFully(m.group(index).trim(), ' ', '-');           ;
        } catch (IllegalStateException ex) {
        }
        return null;
    }
    
    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(TestParsing.class.getName()).log(Level.SEVERE, null, ex);
        }
        String sql = "INSERT INTO `case` (`number`, `name`, `case_date`, `ruling`, `document`, `court_president`, "
                + "`court_vice`, `court_registrar`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String sql_parties = "INSERT INTO `case_parties` (`caseid`, `party_name`) VALUES (?, ?)";
        String sql_courts = "INSERT INTO `court_judges` (`caseid`, `judge`, `ad_hoc`) VALUES (?, ?, ?)";
        try (FileInputStream file = new FileInputStream(new File("D:\\copenhagen\\scraped.xlsx"));
                Connection conn = DriverManager.getConnection(url,username,password);
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                PreparedStatement stmt2 = conn.prepareStatement(sql_parties);
                PreparedStatement stmt3 = conn.prepareStatement(sql_courts)) {
            XSSFWorkbook workbook = new XSSFWorkbook(file);
            Iterator<Row> rowIterator = workbook.getSheetAt(0).iterator();
            Pattern p = Pattern.compile("(.+)( v. |/)(.+)");
            Pattern pres = Pattern.compile("^[Pp]resident[ ]*(.+)$");
            Pattern vice = Pattern.compile("^[Vv]ice(.)*[Pp]resid[\\S]+[ ]*(.+)$");
            Pattern judges = Pattern.compile("^[Jj]udges[ ]*(?!.*(ad hoc))[ ]*(.+)$");
            Pattern adhoc = Pattern.compile("^[Jj]udge[s]* ad hoc[ ]*(.+)$");
            Pattern registrar = Pattern.compile("^[Rr]egistrar[ ]*(.+)$");
            
            Pattern pres_end = Pattern.compile("^ed\\)(.+),[ ]*[Pp]resident[\\S. ]*$");
            Pattern registrar_end = Pattern.compile("^ed\\)(.+),[ ]*[Rr]egistrar[. ]*$");
            rowIterator.next();
            while(rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Cell cell = row.getCell(2);
                String type = null, name = null;
                String[] parties = new String[2];
                String president = null, vice_pres = null, registr = null;
                List<String> court = new ArrayList<>(), adhoc_court = new ArrayList<>();
                String filename = null;
                Integer _case = null;
                Date date = null;
                if (cell != null && cell.getStringCellValue() != null && cell.getCellType() == Cell.CELL_TYPE_STRING) {
                    String [] split = cell.getStringCellValue().split(" -");
                    try {
                        date = new SimpleDateFormat("dd/MM/yyyy").parse(split[0].trim());
                        System.out.println("Case date: " + date);
                    } catch (ParseException ex) {
                        Logger.getLogger(TestParsing.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    if (split.length > 1)
                        type = split[1].substring(0, split[1].indexOf(" of ")).trim();
                    if (split.length > 2) {
                        if (split[2].lastIndexOf("(") != -1) {
                            name = split[2].substring(0, split[2].lastIndexOf("(")).trim();
                            Matcher m = p.matcher(split[2].substring(split[2].lastIndexOf("(")+1, split[2].lastIndexOf(")")).trim());
                            m.find(); 
                            try {
                                if (m.group(1) != null && m.group(3) != null) {
                                    parties[0] = m.group(1);
                                    parties[1] = m.group(3);
                                }
                            } catch (IllegalStateException ex) {
                            }
                        } else
                            name = split[2].trim();
                    }
                }
                cell = row.getCell(1);
                if (cell != null &&  cell.getStringCellValue() != null ) {
                    String value = cell.getStringCellValue();
                    if (value != null) { 
                        filename = case_dir + FileSystems.getDefault().getSeparator() + value.substring(value.lastIndexOf("/")+1, value.length());
                        String left = value.substring(0, value.lastIndexOf("/"));
                        _case = Integer.parseInt(left.substring(left.lastIndexOf("/")+1, left.length()));
                    }
                    PdfReader reader = new PdfReader( new RandomAccessFileOrArray(filename), null);
                    System.out.println("Processing " + filename + "...");
                    PdfReaderContentParser parser = new PdfReaderContentParser(reader);
                    for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                        String text = parser.processContent(i, new SimpleTextExtractionStrategy()).getResultantText();
                        // Possible PDF reading errors, e.g. "prrsent". Use of regexp might be a better option instead of Java contains
                        if (text.toLowerCase().contains("\npresent:") || text.toLowerCase().contains("\npresent :")) {
                            String court_str = text.split("[Pp]r[eé]sent[ ]*:")[1].split("\\.")[0].trim().replace("[\\n\\r]*", "");
                            for (String position: court_str.split(";")) {
                                position = position.replaceAll("\\n", " ").replaceAll("  ", " ").trim();
                                if (position.toLowerCase().startsWith("president")) 
                                    president = getMatch(pres, position, 1);
                                else if (position.toLowerCase().startsWith("vice")) 
                                    vice_pres = getMatch(vice, position, 2);
                                else if (position.toLowerCase().startsWith("registrar")) 
                                    registr = getMatch(registrar, position, 1);
                                else if (position.toLowerCase().startsWith("judge") && !position.toLowerCase().contains("ad hoc")) {
                                    String namelist = getMatch(judges, position, 2);
                                    if (namelist != null)
                                        for (String str: namelist.split(","))
                                            court.add(WordUtils.capitalizeFully(str.trim(), ' ', '-'));
                                } else if (position.toLowerCase().startsWith("judge") && position.toLowerCase().contains("ad hoc")) {
                                    String namelist = getMatch(adhoc, position, 1);
                                    if (namelist != null)
                                        for (String str: namelist.split(","))
                                            adhoc_court.add(WordUtils.capitalizeFully(str.trim(), ' ', '-'));
                                }
                                
                            }
                            break;
                        } else if (text.toLowerCase().contains("(sign") && text.toLowerCase().contains("registrar")) {
                            String [] split = text.split("\\([Ss]ign");
                            for (int k = 1; k < split.length; k++) {
                                String position = split[k].replaceAll("\\n", " ").replaceAll("  ", " ").trim();
                                if (split[k].toLowerCase().contains("president"))
                                    president = getMatch(pres_end, position, 1);
                                if (split[k].toLowerCase().contains("registrar"))
                                    registr = getMatch(registrar_end, position, 1);
                            }
                            break;
                        }
                    }
                    reader.close();
                }
                //Insert extracted results to database
                if (_case != null) {
                    stmt.setInt(1, _case);
                    stmt.setString(2, name);
                    if (date != null)
                        stmt.setDate(3, new java.sql.Date(date.getTime()));
                    else
                        stmt.setNull(3, Types.DATE);
                    stmt.setString(4, type);
                    stmt.setString(5, filename);
                    stmt.setString(6, president);
                    stmt.setString(7, vice_pres);
                    stmt.setString(8, registr);
                    stmt.executeUpdate();
                    ResultSet rs = stmt.getGeneratedKeys();
                    rs.next();
                    int id = rs.getInt(1);
                    for (String party: parties) {
                        stmt2.setInt(1, id);
                        stmt2.setString(2, party);
                        stmt2.execute();
                    }
                    for (String item: court) {
                        stmt3.setInt(1, id);
                        stmt3.setString(2, item);
                        stmt3.setBoolean(3, false);
                        stmt3.execute();
                    }
                    for (String item: adhoc_court) {
                        stmt3.setInt(1, id);
                        stmt3.setString(2, item);
                        stmt3.setBoolean(3, true);
                        stmt3.execute();
                    }
                }
                    
            }  
        } catch (FileNotFoundException ex) {
            Logger.getLogger(TestParsing.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException | SQLException ex) {
            Logger.getLogger(TestParsing.class.getName()).log(Level.SEVERE, null, ex);
        } 
    }
    
}
