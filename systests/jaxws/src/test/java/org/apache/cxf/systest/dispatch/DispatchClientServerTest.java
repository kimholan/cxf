/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.systest.dispatch;

import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.jws.WebService;
import javax.xml.bind.JAXBContext;
import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.AsyncHandler;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Response;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.soap.Addressing;
import javax.xml.ws.soap.AddressingFeature;
import javax.xml.ws.soap.SOAPBinding;
import javax.xml.ws.soap.SOAPFaultException;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import javax.xml.ws.wsaddressing.W3CEndpointReferenceBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.xml.sax.InputSource;

import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.soap.saaj.SAAJUtils;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.jaxws.DispatchImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.cxf.testutil.common.AbstractBusTestServerBase;
import org.apache.cxf.testutil.common.TestUtil;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.hello_world_soap_http.BadRecordLitFault;
import org.apache.hello_world_soap_http.BaseGreeterImpl;
import org.apache.hello_world_soap_http.SOAPService;
import org.apache.hello_world_soap_http.types.GreetMe;
import org.apache.hello_world_soap_http.types.GreetMeLater;
import org.apache.hello_world_soap_http.types.GreetMeResponse;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DispatchClientServerTest extends AbstractBusClientServerTestBase {

    private static final QName SERVICE_NAME
        = new QName("http://apache.org/hello_world_soap_http", "SOAPDispatchService");
    private static final QName PORT_NAME
        = new QName("http://apache.org/hello_world_soap_http", "SoapDispatchPort");

    private static String greeterPort = TestUtil.getPortNumber(DispatchClientServerTest.class);


    @WebService(serviceName = "SOAPService",
        portName = "SoapPort",
        endpointInterface = "org.apache.hello_world_soap_http.Greeter",
        targetNamespace = "http://apache.org/hello_world_soap_http",
        wsdlLocation = "testutils/hello_world.wsdl")
    @Addressing
    public static class GreeterImpl extends BaseGreeterImpl {
    }

    public static class Server extends AbstractBusTestServerBase {
        Endpoint ep;

        protected void run() {
            setBus(BusFactory.getDefaultBus());
            Object implementor = new GreeterImpl();
            String address = "http://localhost:"
                + TestUtil.getPortNumber(DispatchClientServerTest.class)
                + "/SOAPDispatchService/SoapDispatchPort";
            ep = Endpoint.create(SOAPBinding.SOAP11HTTP_BINDING, implementor);
            Map<String, Object> properties = new HashMap<>();
            Map<String, String> nsMap = new HashMap<>();
            nsMap.put("gmns", "http://apache.org/hello_world_soap_http/types");
            properties.put("soap.env.ns.map", nsMap);
            properties.put("disable.outputstream.optimization", "true");

            ep.setProperties(properties);
            ep.publish(address);
            BusFactory.setDefaultBus(null);
            BusFactory.setThreadDefaultBus(null);
        }
        @Override
        public void tearDown() {
            ep.stop();
        }

        public static void main(String[] args) {
            try {
                Server s = new Server();
                s.start();
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(-1);
            } finally {
                System.out.println("done!");
            }
        }
    }

    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue("server did not launch correctly", launchServer(Server.class, true));
        createStaticBus();
    }



    @Test
    public void testSOAPMessage() throws Exception {

        URL wsdl = getClass().getResource("/wsdl/hello_world.wsdl");
        assertNotNull(wsdl);

        SOAPService service = new SOAPService(wsdl, SERVICE_NAME);
        assertNotNull(service);

        Dispatch<SOAPMessage> disp = service.createDispatch(PORT_NAME, SOAPMessage.class, Service.Mode.MESSAGE);
        disp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                "http://localhost:"
                        + greeterPort
                        + "/SOAPDispatchService/SoapDispatchPort");

        // Test request-response
        InputStream is = getClass().getResourceAsStream("resources/GreetMeDocLiteralReq.xml");
        SOAPMessage soapReqMsg = MessageFactory.newInstance().createMessage(null, is);
        assertNotNull(soapReqMsg);
        SOAPMessage soapResMsg = disp.invoke(soapReqMsg);

        assertNotNull(soapResMsg);
        String expected = "Hello TestSOAPInputMessage";
        assertEquals("Response should be : Hello TestSOAPInputMessage", expected,
                DOMUtils.getContent(SAAJUtils.getBody(soapResMsg)
                                             .getFirstChild().getFirstChild()).trim());

        fail("This should not work");
    }

    @Test
    public void testStreamSourceMESSAGE() throws Exception {
        URL wsdl = getClass().getResource("/wsdl/hello_world.wsdl");
        assertNotNull(wsdl);

        SOAPService service = new SOAPService(wsdl, SERVICE_NAME);
        assertNotNull(service);
        Dispatch<StreamSource> disp = service.createDispatch(PORT_NAME, StreamSource.class,
                                                             Service.Mode.MESSAGE);
        disp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                                     "http://localhost:"
                                     + greeterPort
                                     + "/SOAPDispatchService/SoapDispatchPort");

        InputStream is = getClass().getResourceAsStream("resources/GreetMeDocLiteralReq.xml");
        StreamSource streamSourceReq = new StreamSource(is);
        assertNotNull(streamSourceReq);
        StreamSource streamSourceResp = disp.invoke(streamSourceReq);
        assertNotNull(streamSourceResp);
        String expected = "Hello TestSOAPInputMessage";
        assertTrue("Expected: " + expected, StaxUtils.toString(streamSourceResp).contains(expected));

    }

}
