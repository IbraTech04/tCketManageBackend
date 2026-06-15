package com.ibrasoft.tcketmanagebackend.service.wallet;

import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;

/**
 * Abstraction over wallet providers (Apple Wallet, Google Wallet, etc.) that encapsulates the logic of
 * generating, updating, and revoking mobile passes. This is currently a useless facade, since I'm <i>not</i>
 * paying for the Apple Developer Program. 
 */

interface WalletPassService {
    /**
     * Generates a new pass for a ticket and returns the URL that the buyer can use to add it to their wallet. For
     * Apple Wallet, this is a link to a .pkpass file; for Google Wallet, it's a link to the "Save to Google Wallet" button.
     * @param ticket the ticket for which to generate the pass; guaranteed to be in a confirmed order, so the event and ticket type are all set and valid
     * @return a URL that the buyer can use to add the pass to their wallet
     */
    String getAddToWalletUrl(Ticket ticket);

    void updatePass(Ticket ticket);
    void revokePass(Ticket ticket);
}