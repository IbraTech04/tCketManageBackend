package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import com.ibrasoft.tcketmanagebackend.payment.PaymentProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mail.ImapIdleChannelAdapter;
import org.springframework.integration.mail.ImapMailReceiver;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Wires the inbound Interac e-Transfer listener. Uses an {@link ImapIdleChannelAdapter} (IMAP IDLE)
 * so a notification is acted on the moment it lands and the connection auto-reconnects on drops.
 * Received messages flow to {@link EtransferMailHandler}, which confirms the matching order or
 * quarantines the email.
 *
 * <p>The whole graph is conditional on {@code payments.interac.imap.enabled=true}: a deployment that
 * hasn't configured a mailbox simply never instantiates any of these beans.
 */
@Configuration
@ConditionalOnProperty(prefix = "payments.interac.imap", name = "enabled", havingValue = "true")
public class EtransferImapConfig {

    static final String CHANNEL = "etransferMailChannel";

    @Bean(name = CHANNEL)
    MessageChannel etransferMailChannel() {
        return new DirectChannel();
    }

    @Bean
    ImapMailReceiver etransferMailReceiver(PaymentProperties paymentProperties) {
        PaymentProperties.Imap config = paymentProperties.getInterac().getImap();

        ImapMailReceiver receiver = new ImapMailReceiver(buildImapUrl(config));
        receiver.setShouldDeleteMessages(false);
        // Mark handled mail read so the "unseen" search won't re-deliver it on the next IDLE cycle.
        receiver.setShouldMarkMessagesAsRead(true);
        // Keep the folder open after receive so the handler can read the body and copy quarantined
        // messages into the review folder; the handler closes the folder via the closeable header.
        receiver.setAutoCloseFolder(false);
        receiver.setJavaMailProperties(javaMailProperties());
        return receiver;
    }

    @Bean
    ImapIdleChannelAdapter etransferMailAdapter(ImapMailReceiver etransferMailReceiver,
                                                @Qualifier(CHANNEL) MessageChannel etransferMailChannel) {
        ImapIdleChannelAdapter adapter = new ImapIdleChannelAdapter(etransferMailReceiver);
        adapter.setOutputChannel(etransferMailChannel);
        adapter.setAutoStartup(true);
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = CHANNEL)
    MessageHandler etransferMailHandler(EtransferConfirmationService confirmationService,
                                        PaymentProperties paymentProperties) {
        return new EtransferMailHandler(confirmationService,
                paymentProperties.getInterac().getImap().getReviewFolder());
    }

    /** {@code imaps://user:password@host:port/folder}, with credentials percent-encoded. */
    private static String buildImapUrl(PaymentProperties.Imap config) {
        return String.format("imaps://%s:%s@%s:%d/%s",
                encode(config.getUsername()),
                encode(config.getPassword()),
                config.getHost(),
                config.getPort(),
                config.getFolder());
    }

    private static Properties javaMailProperties() {
        Properties props = new Properties();
        props.setProperty("mail.imap.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.setProperty("mail.imap.socketFactory.fallback", "false");
        props.setProperty("mail.store.protocol", "imaps");
        props.setProperty("mail.imaps.ssl.enable", "true");
        props.setProperty("mail.imaps.connectiontimeout", "10000");
        props.setProperty("mail.imaps.timeout", "10000");
        return props;
    }

    private static String encode(String value) {
        if (value == null) {
            return "";
        }
        try {
            // URLEncoder maps space to '+', which is wrong inside a URL authority; fix it up.
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 is always supported
        }
    }
}
