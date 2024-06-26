package akkount.service;

import akkount.entity.Account;
import akkount.entity.Balance;
import akkount.entity.Operation;
import akkount.event.BalanceChangedEvent;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.Metadata;
import io.jmix.core.event.AttributeChanges;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.flowui.UiEventPublisher;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static io.jmix.core.event.EntityChangedEvent.Type.DELETED;
import static io.jmix.core.event.EntityChangedEvent.Type.UPDATED;

@Component(OperationWorker.NAME)
public class OperationWorker {

    public static final String NAME = "akk_OperationWorker";

    private static final Logger log = LoggerFactory.getLogger(OperationWorker.class);

    @Autowired
    private Metadata metadata;

    @Autowired
    private DataManager tdm;

    @Autowired
    private UserDataWorker userDataWorker;

    @Autowired
    private UiEventPublisher uiEventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private volatile boolean balanceChangedEventsEnabled = true;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOperationChanged(EntityChangedEvent<Operation> event) {
        log.debug("onOperationChanged: event={}", event);

        AttributeChanges changes = event.getChanges();
        if (event.getType() == DELETED) {
            removeOperation(
                    changes.getOldValue("opDate"),
                    changes.getOldReferenceId("acc1"),
                    changes.getOldReferenceId("acc2"),
                    changes.getOldValue("amount1"),
                    changes.getOldValue("amount2")
            );
        } else {
            Operation operation = tdm.load(event.getEntityId())/*.view("operation-with-accounts")*/.one();
            if (event.getType() == UPDATED) {
                removeOperation(
                        changes.isChanged("opDate") ? changes.getOldValue("opDate") : operation.getOpDate(),
                        changes.isChanged("acc1") ? changes.getOldReferenceId("acc1") : idOfNullable(operation.getAcc1()),
                        changes.isChanged("acc2") ? changes.getOldReferenceId("acc2") : idOfNullable(operation.getAcc2()),
                        changes.isChanged("amount1") ? changes.getOldValue("amount1") : operation.getAmount1(),
                        changes.isChanged("amount2") ? changes.getOldValue("amount2") : operation.getAmount2()
                );
            }
            addOperation(operation);
            saveUserData(operation);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOperationChangedAndCommitted(EntityChangedEvent<Operation> event) {
        if (balanceChangedEventsEnabled) {
            TransactionTemplate transactionTemplate = new TransactionTemplate(
                    transactionManager, new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
            transactionTemplate.executeWithoutResult(transactionStatus -> {
                uiEventPublisher.publishEvent(new BalanceChangedEvent(this));
            });
        }
    }

    public void enableBalanceChangedEvents(boolean enable) {
        balanceChangedEventsEnabled = enable;
    }

    private void removeOperation(LocalDate opDate, Id<Account> acc1Id, Id<Account> acc2Id,
                                 BigDecimal amount1, BigDecimal amount2) {
        log.debug("removeOperation: opDate={}, acc1Id={}, acc2Id={}, amount1={}, amount2={}", opDate, acc1Id, acc2Id, amount1, amount2);

        if (acc1Id != null) {
            List<Balance> list = getBalanceRecords(opDate, acc1Id);
            if (!list.isEmpty()) {
                for (Balance balance : list) {
                    balance.setAmount(balance.getAmount().add(amount1));
                    tdm.save(balance);
                }
            }
        }

        if (acc2Id != null) {
            List<Balance> list = getBalanceRecords(opDate, acc2Id);
            if (!list.isEmpty()) {
                for (Balance balance : list) {
                    balance.setAmount(balance.getAmount().subtract(amount2));
                    tdm.save(balance);
                }
            }
        }
    }

    private void addOperation(Operation operation) {
        log.debug("addOperation: {}", operation);

        if (operation.getAcc1() != null) {
            List<Balance> list = getBalanceRecords(operation.getOpDate(), Id.of(operation.getAcc1()));
            if (list.isEmpty()) {
                Balance balance = metadata.create(Balance.class);
                balance.setAccount(operation.getAcc1());
                balance.setAmount(operation.getAmount1().negate()
                        .add(previousBalanceAmount(operation.getAcc1(), operation.getOpDate())));
                balance.setBalanceDate(nextBalanceDate(operation.getOpDate()));
                tdm.save(balance);
            } else {
                for (Balance balance : list) {
                    balance.setAmount(balance.getAmount().subtract(operation.getAmount1()));
                    tdm.save(balance);
                }
            }
        }

        if (operation.getAcc2() != null) {
            List<Balance> list = getBalanceRecords(operation.getOpDate(), Id.of(operation.getAcc2()));
            if (list.isEmpty()) {
                Balance balance = metadata.create(Balance.class);
                balance.setAccount(operation.getAcc2());
                balance.setAmount(operation.getAmount2()
                        .add(previousBalanceAmount(operation.getAcc2(), operation.getOpDate())));
                balance.setBalanceDate(nextBalanceDate(operation.getOpDate()));
                tdm.save(balance);
            } else {
                for (Balance balance : list) {
                    balance.setAmount(balance.getAmount().add(operation.getAmount2()));
                    tdm.save(balance);
                }
            }
        }
    }

    private List<Balance> getBalanceRecords(LocalDate opDate, Id<Account> accId) {
        log.debug("getBalanceRecords: opDate={}, accId={}", opDate, accId);

        return tdm.load(Balance.class)
                .query("select b from akk_Balance b " +
                        "where b.account.id = :accountId and b.balanceDate > :balanceDate order by b.balanceDate")
                .parameter("accountId", accId.getValue())
                .parameter("balanceDate", opDate)
                .list();
    }

    private BigDecimal previousBalanceAmount(Account account, LocalDate opDate) {
        log.debug("previousBalanceAmount: acccount={}, opDate={}", account, opDate);

        Optional<Balance> optBalance = tdm.load(Balance.class)
                .query("select b from akk_Balance b " +
                        "where b.account.id = :accountId and b.balanceDate <= :balanceDate order by b.balanceDate desc")
                .parameter("accountId", account.getId())
                .parameter("balanceDate", opDate)
                .maxResults(1)
                .optional();
        return optBalance.map(Balance::getAmount).orElse(BigDecimal.ZERO);
    }

    private LocalDate nextBalanceDate(LocalDate opDate) {
        return opDate.with(TemporalAdjusters.firstDayOfNextMonth());
    }

    private void saveUserData(Operation operation) {
        switch (operation.getOpType()) {
            case EXPENSE:
                userDataWorker.saveEntity(UserDataKeys.OP_EXPENSE_ACCOUNT, operation.getAcc1(), false);
                break;
            case INCOME:
                userDataWorker.saveEntity(UserDataKeys.OP_INCOME_ACCOUNT, operation.getAcc2(), false);
                break;
            case TRANSFER:
                userDataWorker.saveEntity(UserDataKeys.OP_TRANSFER_EXPENSE_ACCOUNT, operation.getAcc1(), false);
                userDataWorker.saveEntity(UserDataKeys.OP_TRANSFER_INCOME_ACCOUNT, operation.getAcc2(), false);
                break;
        }
    }

    @Nullable
    private <T> Id<T> idOfNullable(@Nullable T entity) {
        return entity == null ? null : Id.of(entity);
    }
}
