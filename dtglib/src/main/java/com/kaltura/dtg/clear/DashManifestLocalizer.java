package com.kaltura.dtg.clear;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created by noamt on 21/06/2016.
 */
class DashManifestLocalizer {

    public static final String REPRESENTATION_TAG = "Representation";
    public static final String ADAPTATION_SET_TAG = "AdaptationSet";
    public static final String SEGMENT_TEMPLATE_TAG = "SegmentTemplate";
    public static final String BASE_URL_TAG = "BaseURL";
    public static final String MEDIA_ATTRIBUTE = "media";
    public static final String INITIALIZATION_ATTRIBUTE = "initialization";
    private final byte[] originManifestBytes;
    private final List<DashTrack> keepTracks;
    private byte[] localManifestBytes;
    private XmlPullParser parser;
    private XmlSerializer serializer;


    DashManifestLocalizer(byte[] originManifestBytes, List<DashTrack> keepTracks) {
        this.originManifestBytes = originManifestBytes;
        this.keepTracks = keepTracks;
    }

    public byte[] getLocalManifestBytes() {
        return localManifestBytes;
    }

    void localize() throws IOException {
        try {
            localizeImp();
        } catch (XmlPullParserException e) {
            throw new IOException(e);
        }
        catch (IllegalArgumentException e){
            throw new IOException(e);
        }
    }

    private void skipSubtree() throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }
    
    boolean shouldKeepAdaptationSet(int index) {
        // TODO: make the search more efficient
        for (DashTrack keepTrack : keepTracks) {
            if (keepTrack.getAdaptationIndex() == index) {
                return true;
            }
        }
        return false;
    }
    
    boolean shouldKeepRepresentation(int adaptationIndex, int representationIndex) {
        // TODO: make the search more efficient
        for (DashTrack keepTrack : keepTracks) {
            if (keepTrack.getAdaptationIndex() == adaptationIndex && keepTrack.getRepresentationIndex() == representationIndex) {
                return true;
            }
        }
        return false;

    }
    
    void localizeImp() throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        parser = factory.newPullParser();

        serializer = factory.newSerializer();

        parser.setInput(new ByteArrayInputStream(originManifestBytes), null);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        serializer.setOutput(output, "utf8");



        parser.require(XmlPullParser.START_DOCUMENT, null, null);
        Boolean standalone = (Boolean) parser.getProperty("http://xmlpull.org/v1/doc/properties.html#xmldecl-standalone");
        serializer.startDocument(parser.getInputEncoding(), standalone);
        
        int representationIndex = -1;
        int adaptationSetIndex = -1;
        
        String currentRepresentationId = null;
        
        int eventType;
        while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    copyNamespaces();

                    if (parser.getName().equals(ADAPTATION_SET_TAG)) {

                        adaptationSetIndex++;
                        representationIndex = -1;
                        currentRepresentationId = null;
                        
                        if (!shouldKeepAdaptationSet(adaptationSetIndex)) {
                            skipSubtree();
                            continue;
                        }
                    }
                    
                    if (parser.getName().equals(REPRESENTATION_TAG)) {

                        representationIndex++;
                        currentRepresentationId = parser.getAttributeValue(null, "id");
                        
                        if (!shouldKeepRepresentation(adaptationSetIndex, representationIndex)) {
                            skipSubtree();
                            continue;
                        }
                    }

                    // Start copying the tag
                    serializer.startTag(parser.getNamespace(), parser.getName());
                    switch (parser.getName()){
                        case SEGMENT_TEMPLATE_TAG:
                            handleSegmentTemplate();
                            break;
                        case BASE_URL_TAG:
                            handleBaseURL(currentRepresentationId);
                            break;
                        default:
                            copyTagAttributes();
                            break;
                    }
                    break;
                
                case XmlPullParser.END_TAG:
                    serializer.endTag(parser.getNamespace(), parser.getName());
                    break;

                case XmlPullParser.TEXT:
                    serializer.text(parser.getText());
                    break;
            }
        }
        serializer.endDocument();
        serializer.flush();
        localManifestBytes = output.toByteArray();
    }

    private void handleBaseURL(String id) throws IOException, XmlPullParserException {
        serializer.text(id + ".vtt");
        // Avoid copying the original text.
        // NOTE: if there are child elements in the BaseURL, they will be skipped.
        skipSubtree();
        // We skipped the end tag, but we still need it.
        serializer.endTag(parser.getNamespace(), parser.getName());
    }

    private void copyTagAttributes() throws IOException {
        // copy all attributes
        for (int i = 0, n = parser.getAttributeCount(); i<n; i++) {
            String attributeName = parser.getAttributeName(i);
            String attributeNamespace = parser.getAttributeNamespace(i);
            String attributeValue = parser.getAttributeValue(i);
            serializer.attribute(attributeNamespace, attributeName, attributeValue);
        }
    }

    private void handleSegmentTemplate() throws IOException {
        // copy attributes, but modify the template
        for (int i = 0, n = parser.getAttributeCount(); i<n; i++) {
            String attributeName = parser.getAttributeName(i);
            String attributeNamespace = parser.getAttributeNamespace(i);
            String attributeValue = parser.getAttributeValue(i);

            switch (attributeName) {
                case MEDIA_ATTRIBUTE:
                    attributeValue = "seg-$RepresentationID$-$Number$.m4s"; break;
                case INITIALIZATION_ATTRIBUTE:
                    attributeValue = "init-$RepresentationID$.mp4"; break;
            }

            serializer.attribute(attributeNamespace, attributeName, attributeValue);
        }
    }

    private void copyNamespaces() throws XmlPullParserException, IOException {
        int nsStart = parser.getNamespaceCount(parser.getDepth()-1);
        int nsEnd = parser.getNamespaceCount(parser.getDepth());
        for (int i = nsStart; i < nsEnd; i++) {
            String prefix = parser.getNamespacePrefix(i);
            String ns = parser.getNamespaceUri(i);
            serializer.setPrefix(prefix, ns);
        }
    }

}
