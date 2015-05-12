/*
 * www.zydor.pl
 *
 *
 *
 */
package pswebservice;


import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 *
 * @author www.zydor.pl
 */
public class PSWebServiceClient {
    
    /** @var string Shop URL */
    protected String url;
    /** @var string Authentification key */
    protected String key;
    /** @var boolean is debug activated */	
    protected boolean debug;
    
    private final CloseableHttpClient httpclient;
    private CloseableHttpResponse response;

    /**
     * PrestaShopWebservice constructor. 
     * <code>
     * 
     * try
     * {
     * 	PSWebServiceClient ws = new PSWebServiceClient('http://mystore.com/', 'ZQ88PRJX5VWQHCWE4EE7SQ7HPNX00RAJ', false);
     * 	// Now we have a webservice object to play with
     * }
     * catch (PrestaShopWebserviceException ex)
     * {
     * 	// Handle exception
     * }
     * 
     * </code>
     * @param url Root URL for the shop
     * @param key Authentification key
     * @param debug Debug mode Activated (true) or deactivated (false)
    */
    public PSWebServiceClient(String url,String key,boolean debug){
        this.url    = url;
        this.key    = key;
        this.debug  = debug;
        
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(key, ""));
        
        this.httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();
    }
    
    /**
     * Take the status code and throw an exception if the server didn't return 200 or 201 code
     * @param status_code Status code of an HTTP return
     * @throws pswebservice.PrestaShopWebserviceException
     */
    protected void checkStatusCode(int status_code) throws PrestaShopWebserviceException
    {

            String error_label = "This call to PrestaShop Web Services failed and returned an HTTP status of %d. That means: %s.";            
            switch(status_code)
            {
                    case 200:
                    case 201:	break;
                    case 204: throw new PrestaShopWebserviceException(String.format(error_label, status_code, "No content"));
                    case 400: throw new PrestaShopWebserviceException(String.format(error_label, status_code, "Bad Request"));
                    case 401: throw new PrestaShopWebserviceException(String.format(error_label, status_code, "Unauthorized"));
                    case 404: throw new PrestaShopWebserviceException(String.format(error_label, status_code, "Not Found"));
                    case 405: throw new PrestaShopWebserviceException(String.format(error_label, status_code, "Method Not Allowed"));
                    case 500: throw new PrestaShopWebserviceException(String.format(error_label, status_code, "Internal Server Error"));
                    default: throw new PrestaShopWebserviceException("This call to PrestaShop Web Services returned an unexpected HTTP status of:" + status_code);
            }
    }   
    
    /**
     * Handles request to PrestaShop Webservice. Can throw exception.
     * @param url Resource name
     * @param request
     * @return array status_code, response
     * @throws pswebservice.PrestaShopWebserviceException
     */
    protected HashMap<String,Object> executeRequest(HttpUriRequest request) throws PrestaShopWebserviceException
    {
        
        HashMap<String,Object> returns = new HashMap<>();
        
        try {
            response = httpclient.execute(request);
            Header[] headers =response.getAllHeaders();
            HttpEntity entity = response.getEntity();
            
            if (this.debug)
            {                
		System.out.println("Status:  " + response.getStatusLine());
                System.out.println("====================Header======================");
                for(Header h : headers){
                    System.out.println(h.getName()+" : "+h.getValue());
                }
                System.out.println("====================ResponseBody================");
                System.out.println(readInputStreamAsString(entity.getContent()));	
            }
            

            returns.put("status_code", response.getStatusLine().getStatusCode());
            returns.put("response", entity.getContent());
            returns.put("header", headers );
            //response.close();
            
        } catch (IOException ex) {
            throw new PrestaShopWebserviceException("Bad HTTP response : "+ex.toString());
        }
        
        return returns;
    }
    
    /**
     * Load XML from string. Can throw exception
     * @param responseBody
     * @return parsedXml
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    protected Document parseXML(InputStream responseBody) throws ParserConfigurationException, SAXException, IOException
    {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
	DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        //System.out.println(responseBody);
        return docBuilder.parse(responseBody);
    }
    
    /**
     * Add (POST) a resource
     * <p>Unique parameter must take : <br><br>
     * 'resource' => Resource name<br>
     * 'postXml' => Full XML string to add resource<br><br>
     * @param opt
     * @return xml response
     * @throws pswebservice.PrestaShopWebserviceException
     */
    public Document add(Map<String,Object> opt) throws PrestaShopWebserviceException
    {
		if ( (opt.containsKey("resource") &&  opt.containsKey("postXml")) || (opt.containsKey("url") &&  opt.containsKey("postXml"))  )
                {
                    String completeUrl;
                    completeUrl = (opt.containsKey("resource") ? this.url+"/api/"+ (String) opt.get("resource") : (String) opt.get("url"));
                    String xml = (String)opt.get("postXml");
			if (opt.containsKey("id_shop"))
				completeUrl += "&id_shop="+ (String)opt.get("id_shop");
			if (opt.containsKey("id_group_shop"))
				completeUrl += "&id_group_shop="+(String)opt.get("id_group_shop");
                           
                    StringEntity entity = new StringEntity(xml, ContentType.create("text/xml", Consts.UTF_8));
                    entity.setChunked(true);
                    
                    HttpPost httppost = new HttpPost(completeUrl);
                    httppost.setEntity(entity);
                    
                    HashMap<String,Object> resoult = this.executeRequest(httppost);
                    this.checkStatusCode((Integer)resoult.get("status_code"));
                    
                    try {  
                        Document doc = this.parseXML((InputStream)resoult.get("response"));
                        response.close();
                        return doc;
                    } catch (ParserConfigurationException | SAXException | IOException ex) {
                        throw new PrestaShopWebserviceException("Response XML Parse exception");
                    }
                
		}
		else
                {
			throw new PrestaShopWebserviceException("Bad parameters given");
                }
                
      
    }

    /**
     * Retrieve (GET) a resource
     * <p>Unique parameter must take : <br><br>
     * 'url' => Full URL for a GET request of Webservice (ex: http://mystore.com/api/customers/1/)<br>
     * OR<br>
     * 'resource' => Resource name,<br>
     * 'id' => ID of a resource you want to get<br><br>
     * </p>
     * <code>
     * 
     * try
     * {
     *  PSWebServiceClient ws = new PrestaShopWebservice('http://mystore.com/', 'ZQ88PRJX5VWQHCWE4EE7SQ7HPNX00RAJ', false);
     *  HashMap<String,Object> opt = new HashMap();
     *  opt.put("resouce","orders");
     *  opt.put("id",1);
     *  Document xml = ws->get(opt);
     *	// Here in xml, a XMLElement object you can parse
     * catch (PrestaShopWebserviceException ex)
     * {
     *  Handle exception
     * }
     * 
     * </code>
     * @param opt Map representing resource to get.
     * @return Document response
     * @throws pswebservice.PrestaShopWebserviceException
     */
    public Document get(Map<String,Object> opt) throws PrestaShopWebserviceException
    {
        String completeUrl;
            if (opt.containsKey("url")){
                    completeUrl = (String) opt.get("url");
            }
            else if (opt.containsKey("resource"))
            {
                    completeUrl = this.url +"/api/"+ opt.get("resource");
                    if (opt.containsKey("id"))
                            completeUrl += "/"+opt.get("id");

                    String[] params = new String[]{"filter", "display", "sort", "limit", "id_shop", "id_group_shop"};
                    for (String p : params)
                        if (opt.containsKey("p"))
                            try {
                                    completeUrl += "?"+p+"="+URLEncoder.encode((String)opt.get(p), "UTF-8")+"&";
                                } catch (UnsupportedEncodingException ex) {
                                    throw new PrestaShopWebserviceException("URI encodin excepton: "+ex.toString());
                                }
                      
            }else{
                throw new PrestaShopWebserviceException("Bad parameters given");
            }
            
            
        HttpGet httpget = new HttpGet(completeUrl);
        HashMap<String,Object> resoult = this.executeRequest(httpget);

        this.checkStatusCode((int) resoult.get("status_code"));// check the response validity

        try {  
            Document doc = this.parseXML((InputStream)resoult.get("response"));
            response.close();
            return doc;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new PrestaShopWebserviceException("Response XML Parse exception: "+ex.toString());
        }            
            
    }    
    
    
    private String readInputStreamAsString(InputStream in) 
        throws IOException {

        BufferedInputStream bis = new BufferedInputStream(in);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int result = bis.read();
        while(result != -1) {
          byte b = (byte)result;
          buf.write(b);
          result = bis.read();
        }        
        return buf.toString();
    }  
    
    public String DocumentToString(Document doc) throws TransformerException {
        TransformerFactory transfac = TransformerFactory.newInstance();
        Transformer trans = transfac.newTransformer();
        trans.setOutputProperty(OutputKeys.METHOD, "xml");
        trans.setOutputProperty(OutputKeys.INDENT, "yes");
        trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", Integer.toString(2));

        StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);
        DOMSource source = new DOMSource(doc.getDocumentElement());

        trans.transform(source, result);
        String xmlString = sw.toString();
        
        return xmlString;
    }
   
}
