package tel.schich;

import java.util.List;

public class RestResponse {
    private final List<String> results;

    public RestResponse(List<String> results) {
        this.results = results;
    }

    public List<String> getResults() {
        return results;
    }
}
