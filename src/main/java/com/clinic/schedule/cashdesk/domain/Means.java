package com.clinic.schedule.cashdesk.domain;

/**
 * How a charge was paid (D-87, D-89). A refund goes out by its charge's means; only cash counts for what
 * should be in the drawer.
 */
public enum Means {
    CASH, DEBIT, CREDIT, TRANSFER, QR
}
