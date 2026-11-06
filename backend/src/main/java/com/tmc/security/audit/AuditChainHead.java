package com.tmc.security.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_chain_head")
public class AuditChainHead {

    @Id
    private Short id;

    @Column(name = "last_hash", nullable = false)
    private String lastHash;

    protected AuditChainHead() {
    }

    public Short getId() {
        return id;
    }

    public String getLastHash() {
        return lastHash;
    }

    public void setLastHash(String lastHash) {
        this.lastHash = lastHash;
    }
}
