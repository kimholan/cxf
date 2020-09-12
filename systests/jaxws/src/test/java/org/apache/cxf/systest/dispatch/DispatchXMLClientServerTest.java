/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.systest.dispatch;

import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.cxf.testutil.common.TestUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import java.io.InputStream;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DispatchXMLClientServerTest
        extends AbstractBusClientServerTestBase {

    private static final QName SERVICE_NAME
            = new QName("http://apache.org/hello_world_xml_http/wrapped", "XMLService");

    private static final QName PORT_NAME
            = new QName("http://apache.org/hello_world_xml_http/wrapped", "XMLDispatchPort");

    private static final String port = TestUtil.getPortNumber(DispatchXMLClientServerTest.class);

    @BeforeClass
    public static void startServers() throws Exception {
        createStaticBus();
        assertTrue("server did not launch correctly", launchServer(Server.class, true));
    }

    @Test
    public void testStreamSourceMESSAGE() throws Exception {
        /*URL wsdl = getClass().getResource("/wsdl/hello_world_xml_wrapped.wsdl");
        assertNotNull(wsdl);

        XMLService service = new XMLService(wsdl, serviceName);
        assertNotNull(service);*/
        Service service = Service.create(SERVICE_NAME);
        assertNotNull(service);
        service.addPort(PORT_NAME, "http://cxf.apache.org/bindings/xformat",
                "http://localhost:"
                        + port
                        + "/XMLService/XMLDispatchPort");

        InputStream is = getClass().getResourceAsStream("/messages/XML_GreetMeDocLiteralReq.xml");
        StreamSource reqMsg = new StreamSource(is);
        assertNotNull(reqMsg);

        Dispatch<Source> disp = service.createDispatch(PORT_NAME, Source.class, Service.Mode.MESSAGE);
        Source source = disp.invoke(reqMsg);
        assertNotNull(source);

        String streamString = StaxUtils.toString(source);
        Document doc = StaxUtils.read(new StringReader(streamString));
        assertEquals("greetMeResponse", doc.getFirstChild().getLocalName());
        assertEquals("Hello tli", doc.getFirstChild().getTextContent());
    }

}
