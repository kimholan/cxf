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

import org.apache.cxf.BusFactory;
import org.apache.cxf.annotations.SchemaValidation;
import org.apache.cxf.message.Message;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.cxf.testutil.common.AbstractBusTestServerBase;
import org.apache.cxf.testutil.common.TestUtil;
import org.apache.cxf.ws.addressing.AddressingProperties;
import org.apache.cxf.ws.addressing.AttributedURIType;
import org.apache.hello_world_soap_http.SOAPService;
import org.apache.hello_world_soap_http.types.GreetMeResponse;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.annotation.Resource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Provider;
import javax.xml.ws.Service;
import javax.xml.ws.ServiceMode;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.WebServiceProvider;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.soap.Addressing;
import javax.xml.ws.soap.SOAPBinding;
import java.io.StringReader;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static javax.xml.ws.Service.Mode.MESSAGE;
import static javax.xml.ws.soap.AddressingFeature.Responses.ANONYMOUS;
import static org.apache.cxf.ws.addressing.JAXWSAConstants.ADDRESSING_PROPERTIES_INBOUND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DispatchClientServerTest
        extends AbstractBusClientServerTestBase {

    private static final QName SERVICE_NAME
            = new QName("http://apache.org/hello_world_soap_http", "SOAPDispatchService");

    private static final QName PORT_NAME
            = new QName("http://apache.org/hello_world_soap_http", "SoapDispatchPort");

    private static final String greeterPort = TestUtil.getPortNumber(DispatchClientServerTest.class);

    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue("server did not launch correctly", launchServer(Server.class, true));
        createStaticBus();
    }

    @Test
    public void testCollapseNeeded() throws JAXBException, SAXException {
        for (String[] value : new String[][]{
                {"expected", " expected"},
                {"expected", " expected "},
                {"expected", " expected  "},
                {"expected", "  expected"},
                {"expected", "  expected  "},
                {"expected expected", "  expected         expected"},
        }) {
            String expected = value[0];
            String actual = value[1];

            String responseMessage = dispatchMessage(actual);
            String messageIdValueInService =responseMessage.replaceFirst("(?imsx)^.*(\\|MESSAGE_ID_INBOUND.*MESSAGE_ID_INBOUND\\|).*$","$1");

            try {
                assertEquals(messageIdValueInService, "|MESSAGE_ID_INBOUND|" + expected + "|MESSAGE_ID_INBOUND|", messageIdValueInService);
                fail("This should actually work...");
            } catch (AssertionError cause) {
                cause.printStackTrace();
                // Happening cause it's not working
            }
        }
    }

    @Test
    public void testNoCollapseNeeded() throws Exception {
        for (String messageIDValue : Arrays.asList(
                "",
                "http://cxf.com",
                "mailto:info@kimholland.nl",
                "../%C3%A9dition.html",
                "../édition.html",
                "http://corona.com/prod.html#shirt",
                "../prod.html#shirt",
                "urn:example:org"
        )) {
            String responseMessage = dispatchMessage(messageIDValue);

            assertTrue(responseMessage, responseMessage.contains("|MESSAGE_ID_INBOUND|" + messageIDValue + "|MESSAGE_ID_INBOUND|"));
        }
    }

    private String dispatchMessage(String inputMessageID) {
        URL wsdl = getClass().getResource("/wsdl/hello_world.wsdl");
        assertNotNull(wsdl);

        SOAPService service = new SOAPService(wsdl, SERVICE_NAME);
        assertNotNull(service);

        String template = "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                                  "    <SOAP-ENV:Header>\n" +
                                  "           <Action xmlns=\"http://www.w3.org/2005/08/addressing\">http://apache.org/cxf/systest/ws/addr_feature/AddNumbersPortType/addNumbers</Action>" +
                                  "           <MessageID xmlns=\"http://www.w3.org/2005/08/addressing\">@@MessageID@@</MessageID>" +
                                  "    </SOAP-ENV:Header>\n" +
                                  "    <SOAP-ENV:Body>\n" +
                                  "        <ns4:greetMe xmlns:ns4=\"http://apache.org/hello_world_soap_http/types\">\n" +
                                  "            <ns4:requestType>TestSOAPInputPMessage</ns4:requestType>\n" +
                                  "        </ns4:greetMe>\n" +
                                  "    </SOAP-ENV:Body>\n" +
                                  "</SOAP-ENV:Envelope>";
        String templated = template.replace("@@MessageID@@", inputMessageID);
        SAXSource requestSource = new SAXSource(new InputSource(new StringReader(templated)));
        assertNotNull(requestSource);

        Dispatch<SAXSource> disp = service.createDispatch(PORT_NAME, SAXSource.class, Service.Mode.MESSAGE);
        disp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, "http://localhost:" + greeterPort + "/SOAPDispatchService/SoapDispatchPort");
        disp.getRequestContext().put(Message.SCHEMA_VALIDATION_ENABLED, Boolean.TRUE);
        SAXSource saxSourceResp = disp.invoke(requestSource);
        assertNotNull(saxSourceResp);
        return StaxUtils.toString(saxSourceResp);
    }

    @WebServiceProvider(serviceName = "SOAPService",
            portName = "SoapPort",
            targetNamespace = "http://apache.org/hello_world_soap_http",
            wsdlLocation = "/wsdl/hello_world.wsdl")
    @SchemaValidation
    @ServiceMode(MESSAGE)
    @Addressing(required = true, responses = ANONYMOUS)
    public static class GreeterImpl
            implements Provider<Source> {

        @Resource
        private WebServiceContext webServiceContext;

        @Override
        public Source invoke(Source source) {
            MessageContext messageContext = webServiceContext.getMessageContext();
            AddressingProperties addressingProperties = (AddressingProperties) messageContext.get(ADDRESSING_PROPERTIES_INBOUND);
            AttributedURIType messageID = addressingProperties.getMessageID();
            String messageIDValue = messageID.getValue();

            try {
                GreetMeResponse reponse = new GreetMeResponse();
                reponse.setResponseType("|MESSAGE_ID_INBOUND|" + messageIDValue + "|MESSAGE_ID_INBOUND|");
                JAXBContext jaxbContext = JAXBContext.newInstance("org.apache.hello_world_soap_http.types");
                Marshaller marshaller = jaxbContext.createMarshaller();

                MessageFactory messageFactory = MessageFactory.newInstance();
                SOAPMessage soapMessage = messageFactory.createMessage();
                SOAPBody detail = soapMessage.getSOAPBody();
                marshaller.marshal(reponse, detail);

                return soapMessage.getSOAPPart().getContent();
            } catch (Exception cause) {
                throw new Error(cause);
            }
        }

    }

    public static class Server
            extends AbstractBusTestServerBase {

        Endpoint ep;

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

        @Override
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

            ep.setProperties(properties);
            ep.publish(address);
            BusFactory.setDefaultBus(null);
            BusFactory.setThreadDefaultBus(null);
        }

        @Override
        public void tearDown() {
            ep.stop();
        }

    }

}
