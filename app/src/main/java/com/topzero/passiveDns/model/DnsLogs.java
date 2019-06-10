package com.topzero.passiveDns.model;

import java.util.Date;

public class DnsLogs {
    String domain;
    String domain_ip;
    String dns_client_ip;
    String dns_server_ip;
    Date record_time;

    public DnsLogs(String domain,
                   String domain_ip,
                   String dns_client_ip,
                   String dns_server_ip,
                   Date record_time) {
        this.domain = domain;
        this.domain_ip = domain_ip;
        this.dns_client_ip = dns_client_ip;
        this.dns_server_ip = dns_server_ip;
        this.record_time = record_time;
    }
}
