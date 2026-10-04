package com.android.killapps;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public class PositiveButtonFinderTest {
    private final String[] ids = {"android:id/button1", "com.android.settings:id/button1"};
    private final String[] labels = {"ok", "okay", "确定"};
    private final PositiveButtonFinder<Element> finder = new PositiveButtonFinder<>(new PositiveButtonFinder.Tree<Element>() {
        public CharSequence text(Element n) { return n.getAttribute("text"); }
        public CharSequence description(Element n) { return n.getAttribute("content-desc"); }
        public String id(Element n) { return n.getAttribute("resource-id"); }
        public boolean enabled(Element n) { return !n.getAttribute("enabled").equals("false"); }
        public boolean visible(Element n) { return !n.getAttribute("visible").equals("false"); }
        public boolean clickable(Element n) { return n.getAttribute("clickable").equals("true"); }
        public int childCount(Element n) { return children(n).size(); }
        public Element child(Element n, int i) { return children(n).get(i); }
        public Element parent(Element n) { return n.getParentNode() instanceof Element ? (Element)n.getParentNode() : null; }
        public Element copy(Element n) { return n; }
        public void release(Element n) { }
        private List<Element> children(Element n) {
            List<Element> children = new ArrayList<>();
            for (Node child = n.getFirstChild(); child != null; child = child.getNextSibling())
                if (child instanceof Element) children.add((Element)child);
            return children;
        }
    });
    private Element xml(String text) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }
    @Test public void selectsComposePositiveParentFromActualLineageOs23Dialog() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                getClass().getResourceAsStream("/lineageos-23-force-stop-dialog.xml"));
        Element target = finder.find(doc.getDocumentElement(), ids, labels);
        assertNotNull(target);
        assertEquals("true", target.getAttribute("clickable"));
        assertEquals("", target.getAttribute("resource-id"));
        assertEquals("确定", ((Element)target.getElementsByTagName("node").item(0)).getAttribute("text"));
    }
    @Test public void keepsLegacyPositiveIdSupport() throws Exception {
        Element root = xml("<node><node text='Cancel' clickable='true'/><node text='Translated positive' resource-id='android:id/button1' clickable='true'/></node>");
        assertEquals("android:id/button1", finder.find(root, ids, labels).getAttribute("resource-id"));
    }
    @Test public void supportsSettingsScopedPositiveId() throws Exception {
        Element root = xml("<node><node resource-id='com.android.settings:id/button1' clickable='true'/></node>");
        assertNotNull(finder.find(root, ids, labels));
    }
    @Test public void supportsEnglishMergedSemanticsAndContentDescription() throws Exception {
        Element root = xml("<node><node content-desc='  OK  ' clickable='true'/></node>");
        assertNotNull(finder.find(root, ids, labels));
    }
    @Test public void neverClicksCancelOrDisabledPositiveParent() throws Exception {
        Element root = xml("<node><node text='Cancel' clickable='true'/><node clickable='true' enabled='false'><node text='OK'/></node></node>");
        assertNull(finder.find(root, ids, labels));
    }
    @Test public void rejectsHiddenButtonsAndTextContainingOk() throws Exception {
        Element root = xml("<node><node text='Click OK to force stop' clickable='true'/><node text='OK' clickable='true' visible='false'/></node>");
        assertNull(finder.find(root, ids, labels));
    }
}
