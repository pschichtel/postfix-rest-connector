package tel.schich.postfixrestconnector.mocks;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import org.asynchttpclient.Dsl;
import com.fasterxml.jackson.databind.ObjectMapper;

import tel.schich.postfixrestconnector.Endpoint;
import tel.schich.postfixrestconnector.SocketmapLookupHandler;

public class MockSocketmapLookupHandler extends SocketmapLookupHandler {
    private String data;

    public MockSocketmapLookupHandler(Endpoint endpoint) {
        super(endpoint, Dsl.asyncHttpClient(), new ObjectMapper());
    }

    @Override
    protected void handleRequest(SocketChannel ch, String requestData) throws IOException {
        data = requestData;
        super.handleRequest(ch, requestData);
    }

    public String getData() {
        return data;
    }
}
