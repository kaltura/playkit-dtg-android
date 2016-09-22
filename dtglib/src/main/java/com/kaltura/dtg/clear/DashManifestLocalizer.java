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
    private final byte[] mOriginManifestBytes;
    private final List<DashTrack> mKeepTracks;
    private byte[] mLocalManifestBytes;
    private XmlPullParser mParser;
    private XmlSerializer mSerializer;


    public byte[] getLocalManifestBytes() {
        return mLocalManifestBytes;
    }

    DashManifestLocalizer(byte[] originManifestBytes, List<DashTrack> keepTracks) {
        mOriginManifestBytes = originManifestBytes;
        mKeepTracks = keepTracks;
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
        if (mParser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (mParser.next()) {
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
        for (DashTrack keepTrack : mKeepTracks) {
            if (keepTrack.getAdaptationIndex() == index) {
                return true;
            }
        }
        return false;
    }
    
    boolean shouldKeepRepresentation(int adaptationIndex, int representationIndex) {
        // TODO: make the search more efficient
        for (DashTrack keepTrack : mKeepTracks) {
            if (keepTrack.getAdaptationIndex() == adaptationIndex && keepTrack.getRepresentationIndex() == representationIndex) {
                return true;
            }
        }
        return false;

    }
    
    void localizeImp() throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        mParser = factory.newPullParser();

        mSerializer = factory.newSerializer();

        mParser.setInput(new ByteArrayInputStream(mOriginManifestBytes), null);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mSerializer.setOutput(output, "utf8");



        mParser.require(XmlPullParser.START_DOCUMENT, null, null);
        Boolean standalone = (Boolean) mParser.getProperty("http://xmlpull.org/v1/doc/properties.html#xmldecl-standalone");
        mSerializer.startDocument(mParser.getInputEncoding(), standalone);
        
        int representationIndex = -1;
        int adaptationSetIndex = -1;
        
        int eventType;
        while ((eventType = mParser.next()) != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    copyNamespaces();

                    if (mParser.getName().equals(ADAPTATION_SET_TAG)) {

                        adaptationSetIndex++;
                        representationIndex = -1;
                        
                        if (!shouldKeepAdaptationSet(adaptationSetIndex)) {
                            skipSubtree();
                            continue;
                        }
                    }
                    
                    if (mParser.getName().equals(REPRESENTATION_TAG)) {

                        representationIndex++;
                        
                        if (!shouldKeepRepresentation(adaptationSetIndex, representationIndex)) {
                            skipSubtree();
                            continue;
                        }
                    }

                    // Start copying the tag
                    mSerializer.startTag(mParser.getNamespace(), mParser.getName());
                    switch (mParser.getName()){
                        case SEGMENT_TEMPLATE_TAG:
                            handleSegmentTemplate();
                            break;
                        case BASE_URL_TAG:
                            handleBaseURL(""+adaptationSetIndex);
                            break;
                        default:
                            copyTagAttributes();
                            break;
                    }
                    break;
                
                case XmlPullParser.END_TAG:
                    mSerializer.endTag(mParser.getNamespace(), mParser.getName());
                    break;

                case XmlPullParser.TEXT:
                    mSerializer.text(mParser.getText());
                    break;
            }
        }
        mSerializer.endDocument();
        mSerializer.flush();
        mLocalManifestBytes = output.toByteArray();
    }

    private void handleBaseURL(String id) throws IOException, XmlPullParserException {
        mSerializer.text(id + ".vtt");
        // Avoid copying the original text.
        // NOTE: if there are child elements in the BaseURL, they will be skipped.
        skipSubtree();
        // We skipped the end tag, but we still need it.
        mSerializer.endTag(mParser.getNamespace(), mParser.getName());
    }

    private void copyTagAttributes() throws IOException {
        // copy all attributes
        for (int i=0, n=mParser.getAttributeCount(); i<n; i++) {
            String attributeName = mParser.getAttributeName(i);
            String attributeNamespace = mParser.getAttributeNamespace(i);
            String attributeValue = mParser.getAttributeValue(i);
            mSerializer.attribute(attributeNamespace, attributeName, attributeValue);
        }
    }

    private void handleSegmentTemplate() throws IOException {
        // copy attributes, but modify the template
        for (int i=0, n=mParser.getAttributeCount(); i<n; i++) {
            String attributeName = mParser.getAttributeName(i);
            String attributeNamespace = mParser.getAttributeNamespace(i);
            String attributeValue = mParser.getAttributeValue(i);

            switch (attributeName) {
                case MEDIA_ATTRIBUTE:
                    attributeValue = "seg-$RepresentationID$-$Number$.m4s"; break;
                case INITIALIZATION_ATTRIBUTE:
                    attributeValue = "init-$RepresentationID$.mp4"; break;
            }

            mSerializer.attribute(attributeNamespace, attributeName, attributeValue);
        }
    }

    private void copyNamespaces() throws XmlPullParserException, IOException {
        int nsStart = mParser.getNamespaceCount(mParser.getDepth()-1);
        int nsEnd = mParser.getNamespaceCount(mParser.getDepth());
        for (int i = nsStart; i < nsEnd; i++) {
            String prefix = mParser.getNamespacePrefix(i);
            String ns = mParser.getNamespaceUri(i);
            mSerializer.setPrefix(prefix, ns);
        }
    }

}
