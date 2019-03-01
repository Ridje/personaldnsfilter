package dnsfilter;

/* PersonalDNSFilter 1.5
   Copyright (C) 2019 Ingo Zenz

   This program is free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation; either version 2
   of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

   Find the latest version at http://www.zenz-solutions.de/personaldnsfilter
   Contact:i.z@gmx.net
 */

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.util.StringTokenizer;


import util.Logger;
import util.conpool.Connection;
import util.http.HttpHeader;

public class DNSServer {

    protected InetSocketAddress address;
    protected int timeout;

    public static final int UDP = 0; //Via UDP
    public static final int TCP = 1; //Via TCP
    public static final int DOT = 2; // DNS over TLS
    public static final int DOH = 3; // DNS of HTTPS

    private static DNSServer INSTANCE = new DNSServer(null,0,0);

    static {
        Connection.setPoolTimeoutSeconds(30);
    }

    public static int getProtoFromString(String s) throws IOException{
        s = s.toUpperCase();
        if (s.equals("UDP"))
            return UDP;
        else if (s.equals("TCP"))
            return TCP;
        else if (s.equals("DOT"))
            return DOT;
        else if (s.equals("DOH"))
            return DOH;
        else throw new IOException("Invalid Protocol: "+s);
    }



    protected DNSServer (InetAddress address, int port, int timeout){
        this.address = new InetSocketAddress(address, port);
        this.timeout = timeout;
    }

    public static DNSServer getInstance(){
        return INSTANCE;
    }

    public DNSServer createDNSServer(int protocol, InetAddress address, int port, int timeout, String endPoint) throws IOException {
        switch (protocol) {
            case UDP: return new UDP(address, port, timeout);
            case TCP: return new TCP(address, port, timeout, false);
            case DOT: return new TCP(address, port, timeout,true);
            case DOH: return new DoH(address, port, timeout, endPoint);
            default: throw new IllegalArgumentException("Invalid protocol:"+protocol);
        }
    }

    public DNSServer createDNSServer(String spec, int timeout) throws IOException{
        String[] entryTokens  = spec.split("::");

        String ip = entryTokens[0];
        int port = 53;
        if (entryTokens.length>1) {
            try {
                port = Integer.parseInt(entryTokens[1]);
            } catch (NumberFormatException nfe) {
                throw new IOException("Invalid Port!", nfe);
            }
        }
        int proto = DNSServer.UDP;
        if (entryTokens.length>2)
            proto = DNSServer.getProtoFromString(entryTokens[2]);

        String endPointURL = null;
        if (entryTokens.length>3)
            endPointURL = entryTokens[3];

        if (proto == DNSServer.DOH && endPointURL== null)
            throw new IOException ("Endpoint URL not defined for DNS over HTTPS (DoH)!");

        return getInstance().createDNSServer(proto,InetAddress.getByName(ip),port,timeout,endPointURL);
    }

    public InetAddress getAddress() {
        return address.getAddress();
    }

    public String getProtocolName(){return "";}


    @Override
    public String toString() {
        return address.getAddress().getHostAddress()+" :: "+address.getPort()+"::"+getProtocolName();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || ! (obj.getClass().equals(this.getClass())))
            return false;

       return address.equals(((DNSServer) obj).address);
    }

    protected void readResponseFromStream(DataInputStream in, int size, DatagramPacket response) throws IOException {

        if (size > response.getData().length - response.getOffset()) //existing buffer does not fit
            throw new IOException("Response Buffer to small for response of length "+size);
        in.readFully(response.getData(), response.getOffset(),size);
        response.setLength(size);
    }

    public void resolve(DatagramPacket request, DatagramPacket response) throws IOException {}


}

class UDP extends DNSServer {

    protected UDP(InetAddress address, int port, int timeout) {
        super(address, port, timeout);
    }

    @Override
    public String getProtocolName(){return "UDP";}

    @Override
    public void resolve(DatagramPacket request, DatagramPacket response) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        try {
            request.setSocketAddress(address);
            socket.setSoTimeout(timeout);
            try {
                socket.send(request);
            } catch (IOException eio) {
                throw new IOException("Cannot reach " + address + "!" + eio.getMessage());
            }
            try {
                socket.receive(response);
            } catch (IOException eio) {
                throw new IOException("No DNS Response from " + address);
            }
        } finally {
            socket.close();
        }
    }
}

class TCP extends DNSServer {
    boolean ssl;

    protected TCP(InetAddress address, int port, int timeout, boolean ssl) {
        super(address, port, timeout);
        this.ssl = ssl;
    }

    @Override
    public String getProtocolName(){
        if (ssl) return "DOT";
        else return "TCP";
    }

    @Override
    public void resolve(DatagramPacket request, DatagramPacket response) throws IOException {
        Connection con = Connection.connect(address,timeout,ssl,null, Proxy.NO_PROXY);
        con.setSoTimeout(timeout);
        try {
            DataInputStream in = new DataInputStream (con.getInputStream());
            DataOutputStream out = new DataOutputStream(con.getOutputStream());
            out.writeShort(request.getLength());
            out.write(request.getData(), request.getOffset(), request.getLength());
            out.flush();
            int size = in.readShort();
            readResponseFromStream(in, size, response);
            response.setSocketAddress(address);
            con.release(true);

        } catch (IOException eio) {
            con.release(false);
            throw eio;
        }

    }
}

class DoH extends DNSServer {

    String url;
    String urlHost;
    String reqTemplate;
    InetSocketAddress urlHostAddress;

    protected DoH(InetAddress address, int port, int timeout, String url) throws IOException {
        super(address, port, timeout);
        this.url= url;
        buildTemplate();
        urlHostAddress = new InetSocketAddress(InetAddress.getByAddress(urlHost, address.getAddress()), port);
    }

    @Override
    public String getProtocolName(){return "DOH";}

    private void buildTemplate() throws IOException {
        String user_agent= "Mozilla/5.0 ("+System.getProperty("os.name")+"; "+System.getProperty("os.version")+")";
        HttpHeader REQ_TEMPLATE = new HttpHeader(HttpHeader.REQUEST_HEADER);
        REQ_TEMPLATE.setValue("User-Agent", user_agent);
        REQ_TEMPLATE.setValue("Accept", "application/dns-message");
        REQ_TEMPLATE.setValue("content-type", "application/dns-message");
        REQ_TEMPLATE.setValue("Connection", "keep-alive");
        REQ_TEMPLATE.setRequest("POST "+url+" "+"HTTP/1.1");
        REQ_TEMPLATE.setValue("Content-Length","9999");

        reqTemplate = REQ_TEMPLATE.getServerRequestHeader(false);
        urlHost = REQ_TEMPLATE.remote_host_name;
    }

    private byte[] buildRequestHeader(int length) throws IOException {
       return reqTemplate.replace("\nContent-Length: 9999","\nContent-Length: "+length).getBytes();
    }

    @Override
    public void resolve(DatagramPacket request, DatagramPacket response) throws IOException {

        byte[] reqHeader = buildRequestHeader(request.getLength());
        Connection con = Connection.connect(urlHostAddress, timeout, true, null, Proxy.NO_PROXY);
        try {
            OutputStream out = con.getOutputStream();
            DataInputStream in = new DataInputStream(con.getInputStream());
            out.write(reqHeader);
            out.write(request.getData(), request.getOffset(), request.getLength());
            out.flush();
            HttpHeader responseHeader = new HttpHeader(in, HttpHeader.RESPONSE_HEADER);
            if (responseHeader.getResponseCode() != 200)
                throw new IOException("DoH failed for "+url+"! "+responseHeader.getResponseCode()+" - "+responseHeader.getResponseMessage());

            int size = (int) responseHeader.getContentLength();
            readResponseFromStream(in, size, response);
            response.setSocketAddress(address);
            con.release(true);

        } catch (IOException eio) {
            con.release(false);
            throw eio;
        }

    }
}


