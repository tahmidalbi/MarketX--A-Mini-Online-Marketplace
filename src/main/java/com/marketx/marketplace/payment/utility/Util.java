package com.marketx.marketplace.payment.utility;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketx.marketplace.payment.parametermappings.SSLCommerzInitResponse;
import com.marketx.marketplace.payment.parametermappings.SSLCommerzValidatorResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;

public class Util {

    public static SSLCommerzInitResponse extractInitResponse(String response) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(response, SSLCommerzInitResponse.class);
    }

    public static SSLCommerzValidatorResponse extractValidatorResponse(String response) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(response, SSLCommerzValidatorResponse.class);
    }

    public static String getByOpeningJavaUrlConnection(String stringUrl) throws IOException {
        String output = "";
        URLConnection conn = URI.create(stringUrl).toURL().openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String outputLine;
        while ((outputLine = br.readLine()) != null) {
            output = output + outputLine;
        }
        br.close();
        return output;
    }
}
