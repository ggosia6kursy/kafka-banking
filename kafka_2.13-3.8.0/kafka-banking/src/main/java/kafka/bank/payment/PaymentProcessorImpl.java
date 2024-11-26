package kafka.bank.payment;

import kafka.bank.accounts.Account;
import kafka.bank.accounts.AccountRepository;
import kafka.bank.payment.operationlog.OperationLog;
import kafka.bank.payment.operationlog.OperationLogSender;
import kafka.bank.payment.request.PaymentRequest;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
class PaymentProcessorImpl implements PaymentProcessor {

    private final AccountRepository accountRepository;

    private final OperationLogSender operationLogSender;

    private final BigDecimal totalAmount = new BigDecimal(800);

    public PaymentProcessorImpl(AccountRepository accountRepository, OperationLogSender operationLogSender) {
        this.accountRepository = accountRepository;
        this.operationLogSender = operationLogSender;
    }

    public void process(PaymentRequest paymentRequest) {
        log.info("Processing payment request: {}", paymentRequest);
        // Validate if accounts exist
        Account payerAccount = accountRepository.getAccount(paymentRequest.payerAccountNumber());
        Account recieverAccount = accountRepository.getAccount(paymentRequest.receiverAccountNumber());
        if (payerAccount == null || recieverAccount == null) {
            log.warn("Invalid account numbers");
            failRequest(paymentRequest);
            return;
        }
        // Validate if we have sufficient amount of cash
        BigDecimal totalBefore = recieverAccount.getBalance().add(payerAccount.getBalance());
        if (payerAccount.getBalance().compareTo(paymentRequest.cashAmount()) < 0) {
            log.warn("Invalid account balance to processed");
            failRequest(paymentRequest);
            return;// Insufficient funds to perform668 56
        }
        // Try to process payment
        payerAccount.withdraw(paymentRequest.cashAmount());
        recieverAccount.deposit(paymentRequest.cashAmount());

        // Check if operation went as expected
        BigDecimal totalAfter = recieverAccount.getBalance().add(payerAccount.getBalance());
        if (totalAfter.compareTo(totalBefore) > 0) {
            log.warn("Balance do not match!!");
        }
        BigDecimal currentTotalAmount = accountRepository.getAllAccountIds().stream()
                .map(accountRepository::getAccount)
                .map(Account::getBalance)
                .reduce(BigDecimal::add)
                .orElse(BigDecimal.ZERO);
        // compare totalBefore and totalAfter
        if (totalAmount.compareTo(currentTotalAmount) != 0) {
            failRequest(paymentRequest);
            throw new IllegalStateException("Invalid amount of money in balance " + currentTotalAmount);
        }
        successRequest(paymentRequest);
        accountRepository.getAllAccountIds().forEach(i -> log.info("Account Balance {}", accountRepository.getAccount(i)));

        // if everything is ok log success
    }

    private void failRequest(PaymentRequest paymentRequest) {
        OperationLog operationLog = OperationLog.builder()
                .id(UUID.randomUUID())
                .type(OperationLog.OperationType.FAIL)
                .description("Payment request failed")
                .requestId(paymentRequest.id().toString())
                .timestamp(LocalDateTime.now())
                .build();
        operationLogSender.send(operationLog);
    }

    private void successRequest(PaymentRequest paymentRequest) {
        OperationLog operationLog = OperationLog.builder()
                .id(UUID.randomUUID())
                .type(OperationLog.OperationType.SUCCESS)
                .description("Payment request succeeded")
                .requestId(paymentRequest.id().toString())
                .timestamp(LocalDateTime.now())
                .build();
        operationLogSender.send(operationLog);
    }

}