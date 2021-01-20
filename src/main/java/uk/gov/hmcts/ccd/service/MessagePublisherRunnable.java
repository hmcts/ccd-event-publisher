package uk.gov.hmcts.ccd.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.jms.core.JmsTemplate;
import uk.gov.hmcts.ccd.config.PublishMessageTask;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateEntity;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;

import javax.jms.JMSException;
import javax.jms.Message;
import java.time.LocalDateTime;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

@Slf4j
public class MessagePublisherRunnable implements Runnable {

    private MessageQueueCandidateRepository messageQueueCandidateRepository;
    private JmsTemplate jmsTemplate;
    private PublishMessageTask publishMessageTask;
    private String logPrefix;

    public MessagePublisherRunnable(MessageQueueCandidateRepository messageQueueCandidateRepository,
                                    JmsTemplate jmsTemplate,
                                    PublishMessageTask publishMessageTask) {
        this.messageQueueCandidateRepository = messageQueueCandidateRepository;
        this.jmsTemplate = jmsTemplate;
        this.publishMessageTask = publishMessageTask;
        this.logPrefix = String.format("[Message Type - %s]", publishMessageTask.getMessageType());
    }

    @Override
    public void run() {
        log.debug(String.format("%s Starting publish message task", logPrefix));
        processUnpublishedMessages(PageRequest.of(0, publishMessageTask.getBatchSize()), newArrayList());
        deletePublishedMessages();
        log.debug(String.format("%s Completed publish message task", logPrefix));
    }

    private void processUnpublishedMessages(Pageable pageable,
                                            List<MessageQueueCandidateEntity> processedEntities) {
        Slice<MessageQueueCandidateEntity> unpublishedMessagesPaginated = null;
        boolean hasError = false;

        try {
            unpublishedMessagesPaginated = messageQueueCandidateRepository
                .findUnpublishedMessages(publishMessageTask.getMessageType(), pageable);
            publishMessages(unpublishedMessagesPaginated, processedEntities);
        } catch (Exception e) {
            log.error(String.format("%s Error encountered during processing of "
                + "unpublished messages", logPrefix), e);
            hasError = true;
        }

        if (!hasError && unpublishedMessagesPaginated.hasNext()) {
            processUnpublishedMessages(unpublishedMessagesPaginated.nextPageable(), processedEntities);
        } else if (!processedEntities.isEmpty()) {
            messageQueueCandidateRepository.saveAll(processedEntities);
            log.info(String.format("%s Published %s messages to destination '%s'",
                                   logPrefix, processedEntities.size(), publishMessageTask.getDestination()
            ));
        }
    }

    private String getPropertyValue(JsonNode data, String propertyId) {

        return data.get(propertyId).asText();
    }

    private Message setProperties(Message message, JsonNode data) throws JMSException {
        for (MessageProperties property : MessageProperties.values()) {
            if ((data.has(property.getPropertyId())) && (!(getPropertyValue(
                data, property.getPropertyId()).equals("null")))) {
                message.setStringProperty(property.getPropertySourceId(), getPropertyValue(
                    data, property.getPropertyId())
                );
            }
        }
        return message;
    }

    private void publishMessages(Slice<MessageQueueCandidateEntity> messagesToPublish,
                                 List<MessageQueueCandidateEntity> processedEntities) {
        messagesToPublish.get().forEach(entity -> {
            jmsTemplate.convertAndSend(
                publishMessageTask.getDestination(),
                entity.getMessageInformation(), message -> setProperties(message, entity.getMessageInformation())
            );
            entity.setPublished(LocalDateTime.now());
            processedEntities.add(entity);
        });
    }

    private void deletePublishedMessages() {
        LocalDateTime retentionDate = LocalDateTime.now().minusDays(publishMessageTask.getPublishedRetentionDays());
        int result = messageQueueCandidateRepository
            .deletePublishedMessages(retentionDate, publishMessageTask.getMessageType());
        log.debug(String.format("%s Deleted %s records with publish date before %s",
                                logPrefix, result, retentionDate.toString()
        ));
    }
}
