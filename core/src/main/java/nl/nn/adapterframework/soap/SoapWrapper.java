/*
   Copyright 2013, 2015 Nationale-Nederlanden, 2020 WeAreFrank!

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.adapterframework.soap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.StringTokenizer;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.axis.client.AxisClient;
import org.apache.axis.configuration.NullProvider;
import org.apache.axis.message.SOAPEnvelope;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSSConfig;
import org.apache.ws.security.WSSecurityEngine;
import org.apache.ws.security.message.WSSecHeader;
import org.apache.ws.security.message.WSSecSignature;
import org.apache.ws.security.message.WSSecTimestamp;
import org.apache.ws.security.message.WSSecUsernameToken;
import org.apache.ws.security.util.DOM2Writer;
import org.apache.xml.security.signature.XMLSignature;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import nl.nn.adapterframework.configuration.ConfigurationException;
import nl.nn.adapterframework.core.IPipeLineSession;
import nl.nn.adapterframework.core.SenderException;
import nl.nn.adapterframework.util.CredentialFactory;
import nl.nn.adapterframework.util.LogUtil;
import nl.nn.adapterframework.util.StreamUtil;
import nl.nn.adapterframework.util.TransformerPool;
import nl.nn.adapterframework.util.XmlUtils;

/**
 * Utility class that wraps and unwraps messages from (and into) a SOAP Envelope.
 * 
 * @author Gerrit van Brakel
 */
public class SoapWrapper {
	protected Logger log = LogUtil.getLogger(this);

	private TransformerPool extractBodySoap11;
	private TransformerPool extractBodySoap12;
	private TransformerPool extractHeader;
	private TransformerPool extractFaultCount;
	private TransformerPool extractFaultCode;
	private TransformerPool extractFaultString;
	private static final String NAMESPACE_DEFS_SOAP11 = "soapenv="+SoapVersion.SOAP11.namespace;
	private static final String NAMESPACE_DEFS_SOAP12 = "soapenv="+SoapVersion.SOAP12.namespace;
	private static final String EXTRACT_BODY_XPATH = "/soapenv:Envelope/soapenv:Body/*";
	private static final String EXTRACT_HEADER_XPATH = "/soapenv:Envelope/soapenv:Header/*";
	private static final String EXTRACT_FAULTCOUNTER_XPATH = "count(/soapenv:Envelope/soapenv:Body/soapenv:Fault)";
	private static final String EXTRACT_FAULTCODE_XPATH = "/soapenv:Envelope/soapenv:Body/soapenv:Fault/faultcode";
	private static final String EXTRACT_FAULTSTRING_XPATH = "/soapenv:Envelope/soapenv:Body/soapenv:Fault/faultstring";

	private static SoapWrapper self = null;

	private SoapWrapper() {
		super();
	}

	private void init() throws ConfigurationException {
		try {
			extractBodySoap11  = TransformerPool.getInstance(XmlUtils.createXPathEvaluatorSource(NAMESPACE_DEFS_SOAP11, EXTRACT_BODY_XPATH, "xml", false, null, false));
			extractBodySoap12  = TransformerPool.getInstance(XmlUtils.createXPathEvaluatorSource(NAMESPACE_DEFS_SOAP12, EXTRACT_BODY_XPATH, "xml", false, null, false));
			extractHeader      = TransformerPool.getInstance(XmlUtils.createXPathEvaluatorSource(NAMESPACE_DEFS_SOAP11, EXTRACT_HEADER_XPATH, "xml"));
			extractFaultCount  = TransformerPool.getInstance(XmlUtils.createXPathEvaluatorSource(NAMESPACE_DEFS_SOAP11, EXTRACT_FAULTCOUNTER_XPATH, "text"));
			extractFaultCode   = TransformerPool.getInstance(XmlUtils.createXPathEvaluatorSource(NAMESPACE_DEFS_SOAP11, EXTRACT_FAULTCODE_XPATH, "text"));
			extractFaultString = TransformerPool.getInstance(XmlUtils.createXPathEvaluatorSource(NAMESPACE_DEFS_SOAP11, EXTRACT_FAULTSTRING_XPATH, "text"));
		} catch (TransformerConfigurationException e) {
			throw new ConfigurationException("cannot create SOAP transformer", e);
		}
	}

	public static SoapWrapper getInstance() throws ConfigurationException {
		if (self == null) {
			self = new SoapWrapper();
			self.init();
		}
		return self;
	}

	public void checkForSoapFault(String responseBody, Throwable nested) throws SenderException {
		String faultString = null;
		String faultCode = null;
		int faultCount = 0;
		try {
			faultCount = getFaultCount(responseBody);
			log.debug("fault count=" + faultCount);
			if (faultCount > 0) {
				faultCode = getFaultCode(responseBody);
				faultString = getFaultString(responseBody);
				log.debug("faultCode=" + faultCode + ", faultString=" + faultString);
			}
		} catch (SAXException|IOException e) {
			log.debug("IOException extracting fault message", e);
		} catch (TransformerException e) {
			log.debug("TransformerException extracting fault message:" + e.getMessageAndLocation());
		}
		if (faultCount > 0) {
			String msg = "SOAP fault [" + faultCode + "]: " + faultString;
			log.info(msg);
			throw new SenderException(msg, nested);
		}
	}

	public String getBody(String message) throws SAXException, TransformerException, IOException  {
		return getBody(message, false, null, null);
	}
	
	public String getBody(String message, boolean allowPlainXml, IPipeLineSession session, String soapNamespaceSessionKey) throws SAXException, TransformerException, IOException  {
		String result = extractBodySoap11.transform(message,null,true);
		if (StringUtils.isNotEmpty(result)) {
			if (session!=null && StringUtils.isNotEmpty(soapNamespaceSessionKey)) {
				session.put(soapNamespaceSessionKey, SoapVersion.SOAP11.namespace);
			}
			return result;
		}
		result = extractBodySoap12.transform(message,null,true);
		if (StringUtils.isNotEmpty(result)) {
			if (session!=null && StringUtils.isNotEmpty(soapNamespaceSessionKey)) {
				session.put(soapNamespaceSessionKey, SoapVersion.SOAP12.namespace);
			}
			return result;
		}
		if (session!=null && StringUtils.isNotEmpty(soapNamespaceSessionKey)) {
			session.put(soapNamespaceSessionKey, SoapVersion.NONE.namespace);
		}
		return allowPlainXml ? message : "";
	}


	public String getHeader(String message) throws SAXException, TransformerException, IOException {
		return extractHeader.transform(message, null, true);
	}

	public String getHeader(InputStream request) throws TransformerException, IOException {
		return extractHeader.transform(new StreamSource(request));
	}

	public int getFaultCount(String message) throws SAXException, TransformerException, IOException {
		if (StringUtils.isEmpty(message)) {
			log.warn("getFaultCount(): message is empty");
			return 0;
		}
		String faultCount = extractFaultCount.transform(message, null, true);
		if (StringUtils.isEmpty(faultCount)) {
			log.warn("getFaultCount(): could not extract fault count, result is empty");
			return 0;
		}
		if (log.isDebugEnabled()) {
			log.debug("getFaultCount(): transformation result [" + faultCount + "]");
		}
		return Integer.parseInt(faultCount);
	}

	public String getFaultCode(String message) throws SAXException, TransformerException, IOException {
		return extractFaultCode.transform(message, null, true);
	}

	public String getFaultString(String message) throws SAXException, TransformerException, IOException {
		return extractFaultString.transform(message, null, true);
	}

	public String putInEnvelope(String message, String encodingStyleUri, String targetObjectNamespace) {
		return putInEnvelope(message, encodingStyleUri, targetObjectNamespace, null);
	}

	public String putInEnvelope(String message, String encodingStyleUri, String targetObjectNamespace, String soapHeader) {
		return putInEnvelope(message, encodingStyleUri, targetObjectNamespace, soapHeader, null);
	}

	public String putInEnvelope(String message, String encodingStyleUri, String targetObjectNamespace, String soapHeader, String namespaceDefs) {
		return putInEnvelope(message, encodingStyleUri, targetObjectNamespace, soapHeader, namespaceDefs, null, null, false);
	}

	public String putInEnvelope(String message, String encodingStyleUri, String targetObjectNamespace, String soapHeaderInitial, String namespaceDefs, String soapNamespace, CredentialFactory wsscf, boolean passwordDigest) {
		String soapHeader = "";
		String encodingStyle = "";
		String targetObjectNamespaceClause = "";
		if (!StringUtils.isEmpty(encodingStyleUri)) {
			encodingStyle = " soapenv:encodingStyle=\"" + encodingStyleUri + "\"";
		}
		if (!StringUtils.isEmpty(targetObjectNamespace)) {
			targetObjectNamespaceClause = " xmlns=\"" + targetObjectNamespace + "\"";
		}
		if (StringUtils.isNotEmpty(soapHeaderInitial)) {
			soapHeader = "<soapenv:Header>" + XmlUtils.skipXmlDeclaration(soapHeaderInitial) + "</soapenv:Header>";
		} 
		StringBuilder namespaceClause = new StringBuilder();
		if (StringUtils.isNotEmpty(namespaceDefs)) {
			StringTokenizer st1 = new StringTokenizer(namespaceDefs, ", \t\r\n\f");
			while (st1.hasMoreTokens()) {
				String namespaceDef = st1.nextToken();
				log.debug("namespaceDef [" + namespaceDef + "]");
				int separatorPos = namespaceDef.indexOf('=');
				if (separatorPos < 1) {
					namespaceClause.append(" xmlns=\"" + namespaceDef + "\"");
				} else {
					namespaceClause.append(" xmlns:" + namespaceDef.substring(0, separatorPos) + "=\"" + namespaceDef.substring(separatorPos + 1) + "\"");
				}
			}
			log.debug("namespaceClause [" + namespaceClause + "]");
		}
		String soapns = StringUtils.isNotEmpty(soapNamespace) ? soapNamespace : SoapVersion.SOAP11.namespace;
		message = "<soapenv:Envelope xmlns:soapenv=\"" + soapns + "\"" + encodingStyle + targetObjectNamespaceClause
				+ namespaceClause + ">" + soapHeader + "<soapenv:Body>" + XmlUtils.skipXmlDeclaration(message)
				+ "</soapenv:Body>" + "</soapenv:Envelope>";
		if (wsscf != null) {
			message = signMessage(message, wsscf.getUsername(), wsscf.getPassword(), passwordDigest);
		}
		return message;
	}

	public String putInEnvelope(String message, String encodingStyleUri) {
		return putInEnvelope(message, encodingStyleUri, null);
	}

	public String createSoapFaultMessage(String faultcode, String faultstring) {
		String faultCdataString = "<![CDATA[" + faultstring + "]]>";
		String fault = "<soapenv:Fault>" + "<faultcode>" + faultcode + "</faultcode>" + "<faultstring>"
				+ faultCdataString + "</faultstring>" + "</soapenv:Fault>";
		return putInEnvelope(fault, null, null, null);
	}

	public String createSoapFaultMessage(String faultstring) {
		return createSoapFaultMessage("soapenv:Server", faultstring);
	}

	public String signMessage(String soapMessage, String user, String password, boolean passwordDigest) {
		try {
			WSSecurityEngine secEngine = WSSecurityEngine.getInstance();
			WSSConfig config = secEngine.getWssConfig();
			config.setPrecisionInMilliSeconds(false);

			// create context
			AxisClient tmpEngine = new AxisClient(new NullProvider());
			MessageContext msgContext = new MessageContext(tmpEngine);

			InputStream in = new ByteArrayInputStream(soapMessage.getBytes(StreamUtil.DEFAULT_INPUT_STREAM_ENCODING));
			Message msg = new Message(in);
			msg.setMessageContext(msgContext);

			// create unsigned envelope
			SOAPEnvelope unsignedEnvelope = msg.getSOAPEnvelope();
			Document doc = unsignedEnvelope.getAsDocument();

			// create security header and insert it into unsigned envelope
			WSSecHeader secHeader = new WSSecHeader();
			secHeader.insertSecurityHeader(doc);

			// add a UsernameToken
			WSSecUsernameToken tokenBuilder = new WSSecUsernameToken();
			if (passwordDigest) {
				tokenBuilder.setPasswordType(WSConstants.PASSWORD_DIGEST);
			} else {
				tokenBuilder.setPasswordType(WSConstants.PASSWORD_TEXT);
			}
			tokenBuilder.setUserInfo(user, password);
			tokenBuilder.addNonce();
			tokenBuilder.addCreated();
			tokenBuilder.prepare(doc);

			WSSecSignature sign = new WSSecSignature();
			sign.setUsernameToken(tokenBuilder);
			sign.setKeyIdentifierType(WSConstants.UT_SIGNING);
			sign.setSignatureAlgorithm(XMLSignature.ALGO_ID_MAC_HMAC_SHA1);
			sign.build(doc, null, secHeader);

			tokenBuilder.prependToHeader(secHeader);

			// add a Timestamp
			WSSecTimestamp timestampBuilder = new WSSecTimestamp();
			timestampBuilder.setTimeToLive(300);
			timestampBuilder.prepare(doc);
			timestampBuilder.prependToHeader(secHeader);

			Document signedDoc = doc;

			return DOM2Writer.nodeToString(signedDoc);

		} catch (Exception e) {
			throw new RuntimeException("Could not sign message", e);
		}
	}
}
