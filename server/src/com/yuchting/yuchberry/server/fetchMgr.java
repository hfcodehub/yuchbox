package com.yuchting.yuchberry.server;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyStore;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;

import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.Parser;
import org.htmlparser.nodes.TextNode;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.NodeList;

import com.sun.mail.smtp.SMTPTransport;


public class fetchMgr{
	
	final static int	CHECK_NUM = 50;
	
	String 	m_protocol 	= null;
    String 	m_host 		= null;
    int		m_port		= 0;
    
    String 	m_protocol_send 	= null;
    String 	m_host_send 		= null;
    int		m_port_send			= 0;
    
    String 	m_inBox 	= "INBOX";
       
	String 	m_userName 	= null;
	String 	m_strUserNameFull = null;
	String 	m_password 	= null;
	String	m_userPassword	= null;
	
	int		m_listenPort = 9716;
	
	int		m_fetchInterval = 10;
	
	boolean m_convertToSimpleChar = false;
	
	// Get a Properties object
    Properties m_sysProps = System.getProperties();
    Properties m_sysProps_send = System.getProperties();
    

    // Get a Session object
    Session m_session 	= null;
    Store 	m_store		= null;
    
    Session m_session_send 	= null;
    SMTPTransport m_sendTransport = null;
        	
    Vector m_unreadMailVector 			= new Vector();
    Vector m_unreadMailVector_confirm 	= new Vector();

    // pushed mail index vector 
    Vector m_vectPushedMailIndex = new Vector();
    
    int		m_beginFetchIndex 	= 0;
    int		m_totalMailCount	= 0;
    
    int		m_unreadFetchIndex	= 0;
    boolean m_userSSL			= false;
    
    private Vector	m_recvMailAttach = new Vector();
    
    
    String m_tmpImportContain = new String();
    
    //! is connected?
    berrySvrDeamon	m_currConnect = null;
        
	public void InitConnect(String _configFile) throws Exception{
		
		
		FileInputStream fs = new FileInputStream("config.ini");
		Properties p = new Properties(); 
		p.load(fs);
		
    	m_protocol	= p.getProperty("protocol");
    	m_host		= p.getProperty("host");
    	m_port		= Integer.valueOf(p.getProperty("port")).intValue();
    	
    	m_protocol_send	= p.getProperty("protocol_send");
    	m_host_send		= p.getProperty("host_send");
		m_port_send		= Integer.valueOf(p.getProperty("port_send")).intValue();
		m_listenPort	= Integer.valueOf(p.getProperty("serverPort")).intValue();
		
		m_fetchInterval	= Integer.valueOf(p.getProperty("pushInterval")).intValue() * 1000;
		if(m_fetchInterval <= 1000){
			System.out.println("the pushInterval segment can't be less than 1sec, set the defaul 10 sec now");
			m_fetchInterval = 10000;
		}
		
		if(Integer.valueOf(p.getProperty("userSSL")).intValue() == 1){
			m_userSSL = true;
		}
    	
		m_strUserNameFull		= p.getProperty("account");
		if(m_strUserNameFull.indexOf('@') == -1 || m_strUserNameFull.indexOf('.') == -1){
			throw new Exception("account : xxxxx@xxx.xxx such as 1234@gmail.com");
		}
		
		m_convertToSimpleChar = p.getProperty("convertoSimpleChar").equals("1");
				
    	m_userName	= m_strUserNameFull.substring(0,m_strUserNameFull.indexOf('@'));
    	m_password	= p.getProperty("password");
    	m_userPassword = p.getProperty("userPassword");
    	
    	m_beginFetchIndex = Integer.valueOf(p.getProperty("userFetchIndex")).intValue();
    	
    	fs.close();
		p.clear();
       	
    	ResetSession();

    	ServerSocket t_svr = GetSocketServer(m_userPassword,m_userSSL);
    	Logger.LogOut("prepare account OK <" + m_userName + ">" );
    	
		while(true){
			try{
				
				m_currConnect = new berrySvrDeamon(this, t_svr.accept());
				
			}catch(Exception _e){
				Logger.PrinterException(_e);
	    	}
    	}	
	}
	
	public synchronized void ResetSession()throws Exception{
		
		DestroyConnect();
    	
		if(m_session != null){
			throw new Exception("has been initialize the session");
		}
				
		if(m_protocol == null){
    		m_protocol = "pop3";
    	}else{
    		
    		if(!m_protocol.equals("imap") 
    		&& !m_protocol.equals("pop3") 
    		&& !m_protocol.equals("pop3s") 
    		&& !m_protocol.equals("imaps")){
    			
    			m_protocol = "pop3";
    		}   		
	    }
		
		
		m_session = Session.getInstance(m_sysProps, null);
    	m_session.setDebug(false);
    	
    	if(m_protocol.indexOf("pop3") != -1){
    		m_sysProps.setProperty("mail.pop3.disabletop", "true");
    	}
    			
    	m_store = m_session.getStore(m_protocol);
    	m_store.connect(m_host,m_port,m_userName,m_password);
    	
    	// initialize the smtp transfer
    	//
    	m_sysProps_send.put("mail.smtp.auth", "true");
    	m_sysProps_send.put("mail.smtp.port", Integer.toString(m_port_send));
    	m_sysProps_send.put("mail.smtp.starttls.enable","true");
    	
    	m_session_send = Session.getInstance(m_sysProps_send, null);
    	m_session_send.setDebug(false);
    	
    	m_sendTransport = (SMTPTransport)m_session_send.getTransport(m_protocol_send);
    	
	}
	
	public synchronized void DestroyConnect()throws Exception{
		m_session = null;
		
		if(m_store != null){
			
		    m_unreadMailVector.clear();
		    
		    // wouldn't clear confirm 
		    // the DestroyConnect function will called when the CheckFolder throw 
		    // javaMail exception 
		    // and re-send when client re-connected
		    //
		    //m_unreadMailVector_confirm.clear();
		    
		    // pushed mail index vector 
		    m_vectPushedMailIndex.clear();
		    
			m_store.close();
			m_store = null;
		}
	}
	
	public int GetMailCountWhenFetched(){
		return m_totalMailCount;
	}
	
	public int GetPushInterval(){
		return m_fetchInterval;
	}
	
	public synchronized void SetBeginFetchIndex(int _index){
		m_beginFetchIndex = _index + 1;
		
		try{
			
			BufferedReader in = new BufferedReader(
									new InputStreamReader(
										new FileInputStream("config.ini")));
				
			StringBuffer t_contain = new StringBuffer();
			
			String line = new String();
			while((line = in.readLine())!= null){
				if(line.indexOf("userFetchIndex=") != -1){
					line = line.replaceAll("userFetchIndex=[^\n]*", "userFetchIndex=" + 100);
				}
				
				t_contain.append(line + "\r\n");
			}
			
			in.close();
			
			FileOutputStream os = new FileOutputStream("config.ini");
			os.write(t_contain.toString().getBytes("GB2312"));
			os.close();
			
		}catch(Exception _e){
			Logger.PrinterException(_e);
		}
	}
	
	public void PrepareRepushUnconfirmMail(){
		
		synchronized(this){
			for(int i = 0;i < m_unreadMailVector_confirm.size();i++){
				fetchMail t_confirmMail = (fetchMail)m_unreadMailVector_confirm.elementAt(i);
				
				boolean t_add = true;
				
				for(int j = 0;j < m_unreadMailVector.size();j++){
					
					fetchMail t_sendMail = (fetchMail)m_unreadMailVector.elementAt(j);
					
					if(t_confirmMail.GetMailIndex() == t_sendMail.GetMailIndex()){
						t_add = false;
						break;
					}
				}
				
				if(t_add){
					m_unreadMailVector.add(t_confirmMail);					
					Logger.LogOut("load mail<" + t_confirmMail.GetMailIndex() + "> send again,wait confirm...");
				}
				
			}
			
			m_unreadMailVector_confirm.removeAllElements();
		}
		
	}
	
	public synchronized void PushMail(sendReceive _sendReceive)throws Exception{
		
		final Vector t_unreadMailVector = m_unreadMailVector;
		final Vector t_unreadMailVector_confirm = m_unreadMailVector_confirm;
		
		while(!t_unreadMailVector.isEmpty()){
			
			fetchMail t_mail = (fetchMail)t_unreadMailVector.elementAt(0); 
			
			ByteArrayOutputStream t_output = new ByteArrayOutputStream();
			
			t_output.write(msg_head.msgMail);
			t_mail.OutputMail(t_output);
			
			_sendReceive.SendBufferToSvr(t_output.toByteArray(),false);
			
			SetBeginFetchIndex(t_mail.GetMailIndex());
			
			synchronized(this){	
				t_unreadMailVector.remove(0);
				t_unreadMailVector_confirm.addElement(t_mail);
			}
			
			Logger.LogOut("send mail<" + t_mail.GetMailIndex() + " : " + t_mail.GetSubject() + ">,wait confirm...");
		}
	}
	
	public void CreateTmpSendMailAttachFile(fetchMail _mail)throws Exception{
		
		// create new thread to send mail
		//
		m_recvMailAttach.addElement(_mail);
		
		Logger.LogOut("send mail with attachment " + _mail.GetAttachment().size());
		
		Vector t_list = _mail.GetAttachment();
		
		for(int i = 0;i < t_list.size();i++){
			fetchMail.Attachment t_attachment = (fetchMail.Attachment)t_list.elementAt(i);
			
			String t_filename = "" + _mail.GetSendDate().getTime() + "_" + i + ".satt";
			FileOutputStream fos = new FileOutputStream(t_filename);
			
			for(int j = 0;j < t_attachment.m_size;j++){
				fos.write(0);
			}
			
			Logger.LogOut("store attachment " + t_filename + " size:" + t_attachment.m_size);
			
			fos.close();
		}
	}
	
	public fetchMail FindAttachMail(final long _time){
		// send the file...
		//
		for(int i = 0;i < m_recvMailAttach.size();i++){
			fetchMail t_mail = (fetchMail)m_recvMailAttach.elementAt(i);
			
			if(t_mail.GetSendDate().getTime() == _time){
				m_recvMailAttach.remove(i);
				
				return t_mail;
			}
		}
		
		return null;
	}
	
	public berrySvrDeamon GetClientConnected(){
		return	m_currConnect;
	}
	
	public synchronized void SetClientConnected(berrySvrDeamon _set){
		m_currConnect = _set;
	}
	
	public int GetBeginFetchIndex(){
		return m_beginFetchIndex;
	}
	
	public void SetUnreadFetchIndex(int _index){
		m_unreadFetchIndex = _index;
	}
	
	public int GetUnreadFetchIndex(){
		return m_unreadFetchIndex;
	}
	
	public void CheckFolder()throws Exception{
		
		Folder folder = m_store.getDefaultFolder();
	    if(folder == null) {
	    	throw new Exception("Cant find default namespace");
	    }
	    
	    folder = folder.getFolder("INBOX");
	    if (folder == null) {
	    	throw new Exception("Invalid INBOX folder");
	    }
	    	    
	    folder.open(Folder.READ_ONLY);
	   
	    if(m_totalMailCount != folder.getMessageCount()){
	    	m_totalMailCount = folder.getMessageCount();	    
		    final int t_startIndex = Math.max(m_totalMailCount - Math.min(CHECK_NUM,m_totalMailCount) + 1,
		    									Math.min(m_totalMailCount,m_beginFetchIndex));
		    
		    Message[] t_msgs = folder.getMessages(t_startIndex, m_totalMailCount);
		    
		    for(int i = 0;i < t_msgs.length;i++){
		    	
		    	Message t_msg = t_msgs[i];
		    	
		    	Flags flags = t_msg.getFlags();
	        	Flags.Flag[] flag = flags.getSystemFlags();  
	        	
	        	boolean t_isNew = true;
	        	for(int j = 0; j < flag.length; j++){
	                if (flag[j] == Flags.Flag.SEEN 
	                	&& flag[j] != Flags.Flag.DELETED
	                	&& flag[j] != Flags.Flag.DRAFT) {
	                	
	                    t_isNew = false;
	                    break;      
	                }
	            }      
	        	
		    	if(t_isNew){
		    		
		    		fetchMail t_mail = new fetchMail(m_convertToSimpleChar);
		    		t_mail.SetMailIndex(i + t_startIndex);
		    		ImportMail(t_msg,t_mail);
		    		
		    		m_unreadMailVector.addElement(t_mail);
		    	}
		    }
	    }	       
	    
	    folder.close(false);
	}
	
	public void SendMail(fetchMail _mail)throws Exception{
		
		Message msg = new MimeMessage(m_session_send);
		ComposeMessage(msg,_mail);
	    
		int t_tryTime = 0;
		while(t_tryTime++ < 5){
			try{
				m_sendTransport.connect(m_host_send,m_port_send,m_userName,m_password);
				m_sendTransport.sendMessage(msg, msg.getAllRecipients());
				m_sendTransport.close();
				break;
			}catch(Exception e){}
		}
		
		// delete the tmp files
		//
		for(int i = 0;i < _mail.GetAttachment().size();i++){
			String t_fullname = "" + _mail.GetSendDate().getTime() + "_" + i + ".satt";
			File t_file = new File(t_fullname);
			t_file.delete();
		}		
	}
	
	
	public void MarkReadMail(int _index)throws Exception{
		
		Folder folder = m_store.getDefaultFolder();
	    if(folder == null) {
	    	throw new Exception("Cant find default namespace");
	    }
	    
	    folder = folder.getFolder("INBOX");
	    if (folder == null) {
	    	throw new Exception("Invalid INBOX folder");
	    }
	    try{
			folder.open(Folder.READ_WRITE);  
			
			Message[] t_msg = folder.getMessages(_index, _index);
			
			if(t_msg.length != 0){
				Logger.LogOut("set index " + _index + " read ");
				t_msg[0].setFlag(Flags.Flag.SEEN, true);
			}
	 	    
	    }finally{
	    	
	    	folder.close(false);
	    }	        
	}
			
	public boolean IsConnectState(){
		return m_session != null;
	}
	
	public ServerSocket GetSocketServer(String _userPassword,boolean _ssl)throws Exception{
		
		if(_ssl){
			String	key				= "YuchBerrySvr.key";  
			
			char[] keyStorePass		= _userPassword.toCharArray();
			char[] keyPassword		= _userPassword.toCharArray();
			
			KeyStore ks				= KeyStore.getInstance(KeyStore.getDefaultType());
			
			ks.load(new FileInputStream(key),keyStorePass);
			
			KeyManagerFactory kmf	= KeyManagerFactory.getInstance("SunX509");
			kmf.init(ks,keyPassword);
			
			SSLContext sslContext = SSLContext.getInstance("SSLv3");
			sslContext.init(kmf.getKeyManagers(),null,null);
			  
			SSLServerSocketFactory factory=sslContext.getServerSocketFactory();
			
			SSLServerSocket t_socket = (SSLServerSocket)factory.createServerSocket(m_listenPort); 
			//t_socket.setNeedClientAuth(true);
			
			return t_socket;
			
		}else{
			return new ServerSocket(m_listenPort);
		}		  
	}
	
	static public void ImportMail(Message m,fetchMail _mail)throws Exception{
		
		Address[] a;
		
		// FROM 
		if ((a = m.getFrom()) != null) {
			Vector t_from = _mail.GetFromVect();
			t_from.removeAllElements();
		    for (int j = 0; j < a.length; j++){
		    	t_from.addElement(DecondeName(a[j].toString(),false));
		    }
		}

		// REPLY TO
		if ((a = m.getReplyTo()) != null) {
			Vector t_vect = _mail.GetReplyToVect();
			t_vect.removeAllElements();
		    for (int j = 0; j < a.length; j++){
		    	t_vect.addElement(DecondeName(a[j].toString(),false));
		    }
		}
		
		// CC
		if( (a = m.getRecipients(Message.RecipientType.CC)) != null){
			Vector t_vect = _mail.GetCCToVect();
			t_vect.removeAllElements();
		    for (int j = 0; j < a.length; j++){
		    	t_vect.addElement(DecondeName(a[j].toString(),false));
		    }
		}
		
		// BCC
		if( (a = m.getRecipients(Message.RecipientType.BCC)) != null){
			Vector t_vect = _mail.GetBCCToVect();
			t_vect.removeAllElements();
		    for (int j = 0; j < a.length; j++){
		    	t_vect.addElement(DecondeName(a[j].toString(),false));
		    }
		}

		// TO
		if ((a = m.getRecipients(Message.RecipientType.TO)) != null) {
			Vector t_vect = _mail.GetSendToVect();
			t_vect.removeAllElements();
			
			Vector t_vectGroup = _mail.GetGroupVect();
			t_vectGroup.removeAllElements();
			
		    for (int j = 0; j < a.length; j++) {
		    	
		    	t_vect.addElement(DecondeName(a[j].toString(),false));
			    
				InternetAddress ia = (InternetAddress)a[j];
				
				if (ia.isGroup()) {
				    InternetAddress[] aa = ia.getGroup(false);
				    for (int k = 0; k < aa.length; k++){
				    	t_vectGroup.addElement(DecondeName(aa[k].toString(),false));
				    }
				}
		    }
		}
		
		String mailTitle = ""; 
		Enumeration enumerationHeaderTmp = ((MimeMessage) m).getMatchingHeaders(new String[] { "Subject" });  
		
		while (enumerationHeaderTmp.hasMoreElements()) {  
		    Header header = (Header) enumerationHeaderTmp.nextElement();  
		    mailTitle = header.getValue();  
		}

		_mail.SetSubject(DecondeName(mailTitle,false));
		_mail.SetSendDate(m.getSentDate());
		
		int t_flags = 0;
		Flags.Flag[] sf = m.getFlags().getSystemFlags(); // get the system flags

		for (int i = 0; i < sf.length; i++) {
		    Flags.Flag f = sf[i];
		    if (f == Flags.Flag.ANSWERED)
		    	t_flags |= fetchMail.ANSWERED;
		    else if (f == Flags.Flag.DELETED)
		    	t_flags |= fetchMail.DELETED;
		    else if (f == Flags.Flag.DRAFT)
		    	t_flags |= fetchMail.DRAFT;
		    else if (f == Flags.Flag.FLAGGED)
		    	t_flags |= fetchMail.FLAGGED;
		    else if (f == Flags.Flag.RECENT)
		    	t_flags |= fetchMail.RECENT;
		    else if (f == Flags.Flag.SEEN)
		    	t_flags |= fetchMail.SEEN;
		    else
		    	continue;	// skip it		
		}
		
		_mail.SetFlags(t_flags);
		
		String[] hdrs = m.getHeader("X-Mailer");
		
		if (hdrs != null){
			_mail.SetXMailer(hdrs[0]);
	    }
		
		_mail.ClearAttachment();

		ImportPart(m,_mail);
	}
	
	static private void ImportPart(Part p,fetchMail _mail)throws Exception{
		
		String filename = p.getFileName();
		
		/*
		 * Using isMimeType to determine the content type avoids
		 * fetching the actual content data until we need it.
		 */
		if (p.isMimeType("text/plain")) {
			
		    try{
		    	_mail.SetContain(_mail.GetContain().concat(p.getContent().toString()));
		    }catch(Exception e){
		    	_mail.SetContain(_mail.GetContain().concat("can't decode content " + e.getMessage()));
		    }	    
		    
		} else if(p.isMimeType("text/html")){
			
			try{
				String t_contain = ChangeHTMLCharset(p.getContent().toString());
								
		    	_mail.SetContain_html(_mail.GetContain_html().concat(t_contain));

			    // parser HTML append the plain text
			    //
		    	_mail.SetContain(_mail.GetContain().concat(ParseHTMLText(t_contain)));
		    	
		    }catch(Exception e){
		    	_mail.SetContain_html(_mail.GetContain_html().concat("can't decode content " + e.getMessage()));
		    }
		    
		    
		}else if (p.isMimeType("multipart/*")) {
			
		    Multipart mp = (Multipart)p.getContent();
		    int count = mp.getCount();
		    
		    for (int i = 0; i < count; i++){
		    	ImportPart(mp.getBodyPart(i),_mail);
		    }
		    
		} else if (p.isMimeType("message/rfc822")) {

			ImportPart((Part)p.getContent(),_mail);
			
		} else if(p.isMimeType("application/*")){
			
			// attachment 
			//
			InputStream is = (InputStream)p.getContent();
			int c;
			ByteArrayOutputStream t_os = new ByteArrayOutputStream();
			while ((c = is.read()) != -1){
				t_os.write(c);
			}			
			
			byte[] t_bytes = t_os.toByteArray();
			
			StoreAttachment(_mail.GetMailIndex(), _mail.GetAttachment().size(), t_bytes);
			
			_mail.AddAttachment(p.getFileName(),p.getContentType(),t_bytes.length);
			
		}else if (p instanceof MimeBodyPart){
		
			/*
			 * If we're saving attachments, write out anything that
			 * looks like an attachment into an appropriately named
			 * file.  Don't overwrite existing files to prevent
			 * mistakes.
			 */
			
		    String disp = p.getDisposition();
		    
		    // many mailers don't include a Content-Disposition
		    if (disp != null && disp.equals("ATTACHMENT")) {
		    			    	
				Vector t_vect = _mail.GetAttachment();
								
				if (filename == null){	
				    filename = "Attachment_" + t_vect.size();
				}else{
					filename = DecondeName(filename,true);
				}

			    _mail.AddAttachment(filename, 
			    					p.getContentType(),
			    					StoreAttachment(((MimeBodyPart)p),_mail.GetMailIndex(), t_vect.size()));
			    
		    }
		    
		} else {
			/*
			 * If we actually want to see the data, and it's not a
			 * MIME type we know, fetch it and check its Java type.
			 */
			Object o = p.getContent();
			
			if (o instanceof String) {
			    
				_mail.SetContain(_mail.GetContain().concat((String)o));
			    
			} else if (o instanceof InputStream) {

			    InputStream is = (InputStream)o;
			    int c;
			    ByteArrayOutputStream t_os = new ByteArrayOutputStream();
			    while ((c = is.read()) != -1){
			    	t_os.write(c);
			    }
			    
			    byte[] t_bytes = t_os.toByteArray();
			    
			    StoreAttachment(_mail.GetMailIndex(),_mail.GetAttachment().size(),t_bytes);
			    
			    _mail.AddAttachment("unknownFromat", "application/*",t_bytes.length);
			    
			} else {
				
				_mail.SetContain(_mail.GetContain().concat(o.toString()));
			}			
		}		
	}
	
	static public String ChangeHTMLCharset(String _html){
		
		final String ft_meta = "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=gb2312\" />";
		final int t_charsetIdx = _html.indexOf("charset");
		
		if(t_charsetIdx == -1){
			
			final int t_headIdx = _html.indexOf("<head>");
			if(t_headIdx != -1){
				//
				StringBuffer str = new StringBuffer(_html);
				str.insert(t_headIdx + 6, ft_meta);
				_html = str.toString();
			}else{
				_html = ft_meta + _html;
			}
		}else{
			_html = _html.replaceAll("charset.[^\"]*", "charset=gb2312");
		}
		
		return _html;
	}
	
	static public String ParseHTMLText(String _html){
		StringBuffer t_text = new StringBuffer();
		t_text.append("[yuchberry prompt:the real URL would be shorten by http://is.gd/]\n\n");
		
		StringBuffer t_shorterText = new StringBuffer();
		
		try{
			Parser parser = new Parser(_html,null);
			parser.setEncoding("GB2312");
			
	        NodeList list = parser.parse(new  NodeFilter() {
	        								public boolean accept(Node node) {
	        									return node instanceof TextNode || node instanceof LinkTag ;
	        								}
	        							});
	        
	        Node[] nodes = list.toNodeArray();

            for (int i = 1; i < nodes.length; i++){
                Node nextNode = nodes[i];

                if (nextNode instanceof TextNode){
                    TextNode textnode = (TextNode) nextNode;
                    t_text.append(textnode.getText());
                    t_text.append("\n");
                }else{
                	
                	LinkTag link = (LinkTag)nextNode;
                	t_text.append(GetShortURL(link.getLink()));
                	t_text.append("\n");
                }              
            }            
            
            int t_emptyCharCounter = 0;
            
            for(int i = 0;i < t_text.length();i++){
            	final char t_char = t_text.charAt(i);
            	if(IsEmptyChar(t_char)){
            		if(t_emptyCharCounter++ < 2){
            			t_shorterText.append(t_char);
            		}
            	}else{
            		t_emptyCharCounter = 0;
            		t_shorterText.append(t_char);
            	}            	
            }
            
		}catch(Exception _e	){}
		
		String t_result = t_shorterText.toString();
		
		t_result = t_result.replaceAll("&lt;", "<");
		t_result = t_result.replaceAll("&gt;", ">");
		t_result = t_result.replaceAll("&amp;", "&");
		t_result = t_result.replaceAll("&apos;", "'");
		t_result = t_result.replaceAll("&quot;", "\"");
		t_result = t_result.replaceAll("&nbsp;", " ");	
		
		return t_result;
	}
	
	static private boolean IsEmptyChar(final char _char){
		return _char == ' ' || _char == '\n' || _char == '\t' || _char == '\r';
	}
	
	static private String GetShortURL(String _longURL){
		
		try{
			URL is_gd = new URL("http://is.gd/api.php?longurl=" + _longURL);
			
	        URLConnection yc = is_gd.openConnection();
	        BufferedReader in = new BufferedReader(
	                                new InputStreamReader(yc.getInputStream()));
	        
	        String inputLine = in.readLine();	        
	        in.close();
	        
	        return (inputLine != null && inputLine.length() < _longURL.length()) ? inputLine:_longURL ;
	        
		}catch(Exception _e){}
		
		return _longURL;
		
        
	}

	static public String DecondeName(String _name,boolean _convert)throws Exception{
		
		if(_name.startsWith("=?GB") || _name.startsWith("=?gb") || _name.startsWith("=?UTF-8")){
			_name = MimeUtility.decodeText(_name);
		}else{
			if(_convert){
				_name = new String(_name.getBytes("ISO8859_1"));
			}			
		}
		
		return _name;
	}
	
	public void ComposeMessage(Message msg,fetchMail _mail)throws Exception{
		
		msg.setFrom(new InternetAddress(m_strUserNameFull));
				
	    msg.setRecipients(Message.RecipientType.TO,
					InternetAddress.parse(fetchMail.parseAddressList(_mail.GetSendToVect()), false));
	    if (!_mail.GetReplyToVect().isEmpty()){
			msg.setRecipients(Message.RecipientType.CC,
						InternetAddress.parse(fetchMail.parseAddressList(_mail.GetReplyToVect()), false));
	    }
	    
	    if(!_mail.GetGroupVect().isEmpty()){
	    	msg.setRecipients(Message.RecipientType.BCC,
					InternetAddress.parse(fetchMail.parseAddressList(_mail.GetGroupVect()), false));
	    }
		

	    msg.setSubject(_mail.GetSubject());

	    if(!_mail.GetAttachment().isEmpty()) {
			// Attach the specified file.
			// We need a multipart message to hold the attachment.
		    	
			MimeBodyPart t_containPart = new MimeBodyPart();
			t_containPart.setText(_mail.GetContain());
			
			MimeMultipart t_mainPart = new MimeMultipart();
			t_mainPart.addBodyPart(t_containPart);
			
			Vector t_contain = _mail.GetAttachment();
			
			try{

				for(int i = 0;i< t_contain.size();i++){

					fetchMail.Attachment t_attachment = (fetchMail.Attachment)t_contain.elementAt(i);
					
					MimeBodyPart t_filePart = new MimeBodyPart();
					t_filePart.setFileName(MimeUtility.encodeText(t_attachment.m_name));

					String t_fullname = "" + _mail.GetSendDate().getTime() + "_" + i + ".satt";
					t_filePart.setContent(ReadFileBuffer( t_fullname ), t_attachment.m_type);
					
					t_mainPart.addBodyPart(t_filePart);
				}	
			}catch(Exception _e){
				Logger.LogOut(_e.getMessage());
			}
			
			msg.setContent(t_mainPart);
			
	    } else {
			// If the desired charset is known, you can use
			// setText(text, charset)
			msg.setText(_mail.GetContain());
	    }

	    msg.setHeader("X-Mailer",_mail.GetXMailer());
	    msg.setSentDate(_mail.GetSendDate());
	}
	
	private static byte[] ReadFileBuffer(String _file)throws Exception{
		File t_file = new File(_file);
		byte[] t_buffer = new byte[(int)t_file.length()];
		
		FileInputStream in = new FileInputStream(_file);
		in.read(t_buffer, 0, t_buffer.length);
		
		return t_buffer;
	}
	
	private static  void StoreAttachment(int _mailIndex,int _attachmentIndex,byte[] _contain){
		String t_filename = "" + _mailIndex + "_" + _attachmentIndex + ".att";
		
		File t_file = new File(t_filename);
		if(t_file.exists() && t_file.length() == (long) _contain.length){
			return;
		}
		
		try{

			FileOutputStream fos = new FileOutputStream(t_filename);
			fos.write(_contain);
			
			fos.close();	
		}catch(Exception _e){
			Logger.PrinterException(_e);
		}		
	}
	
	private static  int StoreAttachment(MimeBodyPart p,int _mailIndex,int _attachmentIndex){
		String t_filename = "" + _mailIndex + "_" + _attachmentIndex + ".att";
		
		File t_file = new File(t_filename);		
		try{

			p.saveFile(t_file);
			
			return (int)t_file.length();
			
		}catch(Exception _e){
			Logger.PrinterException(_e);
		}	
		
		return 0;
	}
}