package com.android.killapps;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public class LocalizationTest {
    private Map<String, String> strings(String locale) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/res/" + locale + "/strings.xml"));
        Map<String, String> result = new TreeMap<>(); NodeList nodes = doc.getElementsByTagName("string");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i); result.put(node.getAttribute("name"), node.getTextContent());
        }
        return result;
    }
    private List<String> placeholders(String value) {
        List<String> result = new ArrayList<>(); Matcher m = Pattern.compile("%[0-9]+\\$[sd]").matcher(value);
        while (m.find()) result.add(m.group()); Collections.sort(result); return result;
    }
    @Test public void everyStringHasChineseTranslationWithMatchingArguments() throws Exception {
        Map<String, String> en = strings("values"), zh = strings("values-zh");
        assertEquals(en.keySet(), zh.keySet());
        for (String key : en.keySet()) {
            assertFalse(key, zh.get(key).trim().isEmpty());
            assertEquals(key, placeholders(en.get(key)), placeholders(zh.get(key)));
        }
    }
}
