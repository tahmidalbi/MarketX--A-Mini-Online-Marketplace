package com.marketx.marketplace.payment.parametermappings;

public class SSLCommerzValidatorResponse {
    public String status;
    public String tran_date;
    public String tran_id;
    public String val_id;
    public String amount;
    public String store_amount;
    public String currency;
    public String bank_tran_id;
    public String card_type;
    public String card_no;
    public String card_issuer;
    public String card_brand;
    public String card_issuer_country;
    public String card_issuer_country_code;
    public String currency_type;
    public String currency_amount;
    public String currency_rate;
    public String base_fair;
    public String value_a;
    public String value_b;
    public String value_c;
    public String value_d;
    public String emi_instalment;
    public String emi_amount;
    public String emi_description;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTran_date() { return tran_date; }
    public void setTran_date(String tran_date) { this.tran_date = tran_date; }

    public String getTran_id() { return tran_id; }
    public void setTran_id(String tran_id) { this.tran_id = tran_id; }

    public String getVal_id() { return val_id; }
    public void setVal_id(String val_id) { this.val_id = val_id; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public String getStore_amount() { return store_amount; }
    public void setStore_amount(String store_amount) { this.store_amount = store_amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getBank_tran_id() { return bank_tran_id; }
    public void setBank_tran_id(String bank_tran_id) { this.bank_tran_id = bank_tran_id; }

    public String getCard_type() { return card_type; }
    public void setCard_type(String card_type) { this.card_type = card_type; }

    public String getCard_no() { return card_no; }
    public void setCard_no(String card_no) { this.card_no = card_no; }

    public String getCurrency_type() { return currency_type; }
    public void setCurrency_type(String currency_type) { this.currency_type = currency_type; }

    public String getCurrency_amount() { return currency_amount; }
    public void setCurrency_amount(String currency_amount) { this.currency_amount = currency_amount; }

    public String getCurrency_rate() { return currency_rate; }
    public void setCurrency_rate(String currency_rate) { this.currency_rate = currency_rate; }
}
