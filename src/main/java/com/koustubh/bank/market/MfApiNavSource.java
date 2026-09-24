package com.koustubh.bank.market;

import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Real mutual fund NAVs from https://www.mfapi.in, a free public API that republishes AMFI's daily NAV data.
 * Response: {"meta": {"scheme_name": ..., "fund_house": ..., "scheme_category": ...},
 *            "data": [{"date": "18-09-2026", "nav": "89.85690"}, ...]} (newest first).
 */
@Component
@Profile("!test")
public class MfApiNavSource implements NavSource {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    record Response(Map<String, Object> meta, List<Map<String, String>> data) {
    }

    private final RestClient client;

    public MfApiNavSource() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl("https://api.mfapi.in").requestFactory(factory).build();
    }

    @Override
    public FundHistory fetch(long schemeCode) {
        Response r = client.get().uri("/mf/{code}", schemeCode).retrieve().body(Response.class);
        if (r == null || r.data() == null || r.data().isEmpty()) {
            throw new IllegalStateException("No NAV data for scheme " + schemeCode);
        }
        List<NavPoint> navs = r.data().stream()
                .map(p -> new NavPoint(LocalDate.parse(p.get("date"), DATE), new BigDecimal(p.get("nav"))))
                .filter(p -> p.nav().signum() > 0)
                .toList();
        return new FundHistory(schemeCode, String.valueOf(r.meta().get("scheme_name")),
                String.valueOf(r.meta().get("fund_house")), String.valueOf(r.meta().get("scheme_category")), navs);
    }
}
