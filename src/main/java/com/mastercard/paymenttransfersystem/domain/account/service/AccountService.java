package com.mastercard.paymenttransfersystem.domain.account.service;

import com.mastercard.paymenttransfersystem.domain.account.model.Account;
import com.mastercard.paymenttransfersystem.domain.account.model.AccountState;
import com.mastercard.paymenttransfersystem.domain.account.model.AccountStatementItem;
import com.mastercard.paymenttransfersystem.domain.account.model.AccountStatementItemType;
import com.mastercard.paymenttransfersystem.domain.account.model.exception.AccountNotFoundException;
import com.mastercard.paymenttransfersystem.domain.account.model.exception.InsufficientFundsException;
import com.mastercard.paymenttransfersystem.domain.account.model.exception.InvalidAmountException;
import com.mastercard.paymenttransfersystem.domain.account.model.exception.SelfTransferException;
import com.mastercard.paymenttransfersystem.domain.account.repository.AccountRepository;
import com.mastercard.paymenttransfersystem.domain.transaction.model.Transaction;
import com.mastercard.paymenttransfersystem.domain.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The service that handles requested operations for the account domain
 */
@Service
@RequiredArgsConstructor
public class AccountService implements IAccountService {

    private final AccountRepository accountRepository;
    private final TransactionService transactionService;
    @Value("${accounts.mini-statement.size}")
    private Long sizeOfMiniStatement;

    /**
     * @see IAccountService for a general description of the overridden method
     * @throws AccountNotFoundException if no account with given id exists
     */
    @Override
    public Account getAccountById(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    /**
     * @see IAccountService for a general description of the overridden method
     * @throws AccountNotFoundException if no account with given id exists
     * Retrieves the latest transaction of the account with the given id,
     * and maps them to a list of AccountStatementItem objects
     */
    @Override
    public List<AccountStatementItem> getMiniStatement(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }

        Collection<Transaction> latestTransactions = transactionService.getLatestTransactions(accountId, sizeOfMiniStatement);
        return mapToAccountStatementItems(accountId, latestTransactions);
    }

    /**
     * @see IAccountService for a general description of the overridden method
     * transfer of money is a five-step proces:
     * 1. Check accounts exist,
     * 2. validate the transfer request
     * 3. debit the sender's account
     * 4. credit the recipient's account
     * 5. create a new transaction object in the repository
     */
    @Override
    @Transactional
    public Account transferMoney(Long senderAccountId, Long recipientAccountId, BigDecimal amount) {
        if(!accountRepository.existsById(senderAccountId)) {
            throw new AccountNotFoundException(senderAccountId);
        }
        if(!accountRepository.existsById(recipientAccountId)) {
            throw new AccountNotFoundException(recipientAccountId);
        }

        // Validate request
        BigDecimal senderBalance = accountRepository.getAccountBalance(senderAccountId);

        if (senderBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(senderAccountId);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException();
        }
        if (senderAccountId.equals(recipientAccountId)) {
            throw new SelfTransferException();
        }

        // Subtract/debit from sender's account
        accountRepository.updateAccountBalance(amount.negate(), senderAccountId);

        // Add/credit to recipient's account
        accountRepository.updateAccountBalance(amount, recipientAccountId);

        // Save transaction
        transactionService.saveTransaction(Transaction.builder()
                .senderAccountId(senderAccountId)
                .recipientAccountId(recipientAccountId)
                .amount(amount)
                .currency("USD")
                .build());
        return getAccountById(senderAccountId);
    }

    @Override
    public List<Account> getAll() {
        return accountRepository.findAll();
    }

    @Override
    public Account createAccount(Account account) {
        return accountRepository.save(account);
    }

    @Override
    public Account updateAccount(Long accountId, Account account) {
        Account existingAccount = getAccountById(accountId);
        return accountRepository.save(
                existingAccount.toBuilder()
                        .currency(account.getCurrency())
                        .state(account.getState())
                        .build());
    }

    @Override
    public Account deleteAccount(Long accountId) {
        Account existingAccount = getAccountById(accountId);
        return accountRepository.save(
                existingAccount.toBuilder()
                        .state(AccountState.SET_FOR_DELETION)
                        .build());
    }

    /**
     * A method used to convert a list of Transaction objects into a list of AccountStatementItem objects
     * @param accountId the id of the account which a statement is requested for
     * @param latestTransactions the list of Transaction objects that is to be mapped to a list of AccountStatementItem objects
     * @return a list of AccounstStatementItem objects, each representing a transaction in the statement
     */
    private List<AccountStatementItem> mapToAccountStatementItems(Long accountId, Collection<Transaction> latestTransactions) {
        return latestTransactions.stream().map(transaction -> {
            AccountStatementItemType type = AccountStatementItemType.DEBIT;
            Long idOfOtherParty = transaction.getRecipientAccountId();
            if (transaction.getRecipientAccountId().equals(accountId)) {
                type = AccountStatementItemType.CREDIT;
                ;
                idOfOtherParty = transaction.getSenderAccountId();
            }
            return new AccountStatementItem().toBuilder()
                    .accountId(idOfOtherParty)
                    .amount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .type(type)
                    .transactionDate(transaction.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }
}