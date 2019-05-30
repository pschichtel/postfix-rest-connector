package tel.schich.postfixrestconnector.mocks;

import java.nio.channels.SocketChannel;
import java.util.List;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.Param;
import com.fasterxml.jackson.databind.ObjectMapper;

import tel.schich.postfixrestconnector.Endpoint;
import tel.schich.postfixrestconnector.PolicyRequestHandler;

public class MockPolicyRequestHandler extends PolicyRequestHandler {
    private List<Param> data;

    public MockPolicyRequestHandler(Endpoint endpoint) {
        super(endpoint, Dsl.asyncHttpClient(), new ObjectMapper());
    }

    @Override
    protected void handleRequest(SocketChannel ch, List<Param> params) {
        data = params;
        super.handleRequest(ch, params);
    }

    public List<Param> getData() {
        List<Param> copy = this.data;
        this.data = null;
        return copy;
    }

}
