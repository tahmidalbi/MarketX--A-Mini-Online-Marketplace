package com.marketx.marketplace.payment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

import com.marketx.marketplace.payment.parametermappings.SSLCommerzInitResponse;
import com.marketx.marketplace.payment.parametermappings.SSLCommerzValidatorResponse;
import com.marketx.marketplace.payment.utility.ParameterBuilder;
import com.marketx.marketplace.payment.utility.Util;

public class SSLCommerz {

    private String storeId;
    private String storePass;
    private boolean storeTestMode;

    private String[] keyList;
    private String generateHash;
    private String error;

    private String sslczURL = "https://securepay.sslcommerz.com/";
    private final String submitURL = "gwprocess/v4/api.php";
    private final String validationURL = "validator/api/validationserverAPI.php";

    public SSLCommerz(String storeId, String storePass, boolean testMode) throws Exception {
        if (!storeId.isEmpty() && !storePass.isEmpty()) {
            this.storeId = storeId;
            this.storePass = storePass;
            this.setSSLCzTestMode(testMode);
        } else {
            throw new Exception("Please provide Store ID and Password to initialize SSLCommerz");
        }
    }

    private void setSSLCzTestMode(boolean testMode) {
        this.storeTestMode = testMode;
        if (testMode) {
            // Use sandbox URL but keep the store credentials supplied by the caller
            this.sslczURL = "https://sandbox.sslcommerz.com/";
        }
    }

    public String initiateTransaction(Map<String, String> postData, boolean isGetGatewayList) throws Exception {
        postData.put("store_id", this.storeId);
        postData.put("store_passwd", this.storePass);
        String response = this.sendPost(postData);

        if (response == null || response.isBlank()) {
            throw new Exception("SSLCommerz returned an empty response. The sandbox may be unreachable.");
        }
        if (response.trim().startsWith("<")) {
            String preview = response.length() > 200 ? response.substring(0, 200) : response;
            throw new Exception("SSLCommerz returned HTML instead of JSON. "
                    + "The sandbox endpoint may have changed or is temporarily down. "
                    + "Response preview: " + preview);
        }

        SSLCommerzInitResponse resp = Util.extractInitResponse(response);
        if ("SUCCESS".equals(resp.status)) {
            if (!isGetGatewayList) {
                return resp.getGatewayPageURL();
            }
        } else {
            throw new Exception("SSLCommerz declined: " + resp.failedreason);
        }
        return response;
    }

    public boolean orderValidate(String merchantTrnxnId, String merchantTrnxnAmount,
                                  String merchantTrnxnCurrency,
                                  Map<String, String> requestParameters)
            throws IOException, NoSuchAlgorithmException {

        boolean hashVerified = this.ipnHashVerify(requestParameters);
        if (hashVerified) {
            String encodedValID = URLEncoder.encode(requestParameters.get("val_id"),
                    Charset.forName("UTF-8").displayName());
            String encodedStoreID = URLEncoder.encode(this.storeId,
                    Charset.forName("UTF-8").displayName());
            String encodedStorePassword = URLEncoder.encode(this.storePass,
                    Charset.forName("UTF-8").displayName());

            String validUrl = this.sslczURL + this.validationURL
                    + "?val_id=" + encodedValID
                    + "&store_id=" + encodedStoreID
                    + "&store_passwd=" + encodedStorePassword
                    + "&v=1&format=json";

            String json = Util.getByOpeningJavaUrlConnection(validUrl);
            if (!json.isEmpty()) {
                SSLCommerzValidatorResponse resp = Util.extractValidatorResponse(json);
                if (resp.status.equals("VALID") || resp.status.equals("VALIDATED")) {
                    if (merchantTrnxnId.equals(resp.tran_id)
                            && (Math.abs(Double.parseDouble(merchantTrnxnAmount)
                                    - Double.parseDouble(resp.currency_amount)) < 1)
                            && merchantTrnxnCurrency.equals(resp.currency_type)) {
                        return true;
                    } else {
                        this.error = "Currency amount not matching";
                        return false;
                    }
                } else {
                    this.error = "Transaction expired or failed";
                    return false;
                }
            } else {
                this.error = "Unable to get transaction JSON status";
                return false;
            }
        } else {
            this.error = "Unable to verify hash";
            return false;
        }
    }

    private Boolean ipnHashVerify(final Map<String, String> requestParameters)
            throws UnsupportedEncodingException, NoSuchAlgorithmException {

        String verifySign = requestParameters.get("verify_sign");
        String verifyKey = requestParameters.get("verify_key");
        if (verifySign == null || verifyKey == null
                || verifySign.isEmpty() || verifyKey.isEmpty()) {
            return false;
        }

        keyList = verifyKey.split(",");
        TreeMap<String, String> sortedMap = new TreeMap<>();
        for (final String k : keyList) {
            sortedMap.put(k, requestParameters.getOrDefault(k, ""));
        }

        final String hashedPass = this.md5(this.storePass);
        sortedMap.put("store_passwd", hashedPass);

        String hashString = ParameterBuilder.getParamsString(sortedMap, false);
        generateHash = this.md5(hashString);
        return generateHash.equals(verifySign);
    }

    private String md5(String s) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        byte[] bytesOfMessage = s.getBytes("UTF-8");
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] theDigest = md.digest(bytesOfMessage);
        StringBuilder sb = new StringBuilder();
        for (byte b : theDigest) {
            sb.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
        }
        return sb.toString();
    }

    private String sendPost(Map<String, String> postData) throws IOException {
        return SSLCommerz.post(this.sslczURL + this.submitURL, postData);
    }

    private static String post(String uri, Map<String, String> postData) throws IOException {
        String output = "";
        String urlParameters = ParameterBuilder.getParamsString(postData, true);
        byte[] postDataBytes = urlParameters.getBytes();

        HttpURLConnection con = (HttpURLConnection) URI.create(uri).toURL().openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout(10000);
        con.setReadTimeout(10000);
        con.setInstanceFollowRedirects(false);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        con.setRequestProperty("charset", "utf-8");
        con.setRequestProperty("Content-Length", Integer.toString(postDataBytes.length));
        con.setUseCaches(false);
        con.setDoOutput(true);
        con.getOutputStream().write(postDataBytes);

        int status = con.getResponseCode();
        java.io.InputStream stream = (status >= 400) ? con.getErrorStream() : con.getInputStream();
        if (stream == null) {
            throw new IOException("SSLCommerz returned HTTP " + status + " with no body");
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        String outputLine;
        while ((outputLine = br.readLine()) != null) {
            output = output + outputLine;
        }
        br.close();
        System.out.println("[SSLCommerz] HTTP " + status + " response: " + output);
        return output;
    }

    public String getError() {
        return error;
    }
}
