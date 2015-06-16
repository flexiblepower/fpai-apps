package org.flexiblepower.protocol.mielegateway;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.flexiblepower.protocol.mielegateway.api.ActionPerformer;
import org.flexiblepower.protocol.mielegateway.api.ActionResult;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriver;
import org.flexiblepower.protocol.mielegateway.xml.ActionResultParser;
import org.flexiblepower.protocol.mielegateway.xml.XMLUtil;
import org.w3c.dom.Document;

public class MieleResourceDriverWrapper implements ActionPerformer, Closeable {
    private URL detailsURL;
    private MieleResourceDriver<?, ?> driver;

    public URL getDetailsURL() {
        return detailsURL;
    }

    public void setDetailsURL(URL detailsURL) {
        this.detailsURL = detailsURL;
    }

    public void setDriver(MieleResourceDriver<?, ?> driver) {
        this.driver = driver;
    }

    public final void updateState(Map<String, String> information, Map<String, URL> actions) {
        this.actions = actions; // Cache the actions;
        driver.updateState(information);
    }

    private Map<String, URL> actions = Collections.emptyMap();

    public final Set<String> getActiveActions() {
        return actions.keySet();
    }

    @Override
    public ActionResult performAction(String action) {
        ActionResult result = null;
        URL url = actions.get(action);
        if (url != null) {
            Document document = XMLUtil.get().parseXml(url);
            if (document != null) {
                result = ActionResultParser.get().parse(document.getDocumentElement());
            }
        }

        if (result != null) {
            return result;
        } else {
            return new ActionResult(false, "Action could not be performed, see logs for more info", "Missing action");
        }
    }

    @Override
    public String toString() {
        return driver.getClass().getSimpleName();
    }

    @Override
    public void close() throws IOException {
        driver.close();
    }
}
