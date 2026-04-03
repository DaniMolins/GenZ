package src;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class Lexer {

    File reservedWordsFile = new File("../files/reserved_words.csv");
    File sourceFile = new File("../files/code.txt");

    FileReader codeReader;

    private StringBuilder token; 

    private char currentChar; 
    private int lineNumber; 

    private HashMap<String, String> dictionary;

    public Lexer () {
        dictionary = new HashMap<>();
        token = new StringBuilder();

        loadKeywordsFromCSV();
        try {
            codeReader = new FileReader(sourceFile);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read source file", e);
        }
    }

    public void loadKeywordsFromCSV() {
        try (FileReader reader = new FileReader(reservedWordsFile)) {

            while(reader.ready()) {
                StringBuilder keyword = new StringBuilder();
                StringBuilder tokenType = new StringBuilder();

                char c;
                while((c = (char) reader.read()) != ',') {
                    keyword.append(c);
                }
                while((c = (char) reader.read()) != '\n') {
                    tokenType.append(c);
                }

                dictionary.put(keyword.toString(), tokenType.toString());
            }
            
            
        } catch (Exception e) {
            e.printStackTrace();

        }
    }


}