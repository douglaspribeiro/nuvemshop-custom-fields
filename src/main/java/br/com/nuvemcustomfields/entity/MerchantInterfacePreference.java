package br.com.nuvemcustomfields.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "merchant_interface_preferences")
public class MerchantInterfacePreference {

    public enum Choice {
        PREVIEW, CLASSIC, DECLINE
    }

    @Id
    @Column(name = "store_id")
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "interface_choice", nullable = false, length = 16)
    private Choice choice;

    protected MerchantInterfacePreference() {}

    public Choice getChoice() {
        return choice;
    }
}
