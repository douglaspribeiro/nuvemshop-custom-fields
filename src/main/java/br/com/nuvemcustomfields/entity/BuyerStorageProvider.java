package br.com.nuvemcustomfields.entity;

/** Durable location of a buyer file; never inferred from the current upload destination. */
public enum BuyerStorageProvider {
    AWS_S3,
    OCI_OBJECT_STORAGE
}
