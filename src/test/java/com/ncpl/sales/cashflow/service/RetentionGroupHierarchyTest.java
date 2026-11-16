package com.ncpl.sales.cashflow.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RetentionGroupHierarchyTest {
 private Map<String,String> groups(){Map<String,String> g=new HashMap<>();g.put("Retention Amounts","Sundry Debtors");g.put("Sundry Debtors","Current Assets");g.put("Current Assets","Primary");g.put("Project A","Retention Amounts");return g;}
 @Test void directAndNestedMembershipDoNotRequireRetentionInLedgerName(){assertTrue(RetentionGroupHierarchy.isRetentionLedger("Retention Amounts","Retention Amounts",groups()));assertTrue(RetentionGroupHierarchy.isRetentionLedger("Project A","Retention Amounts",groups()));}
 @Test void similarlyNamedLedgerOutsideGroupIsNotRetention(){assertFalse(RetentionGroupHierarchy.isRetentionLedger("Sundry Debtors","Retention Amounts",groups()));}
 @Test void rootMustBelongToTheConfirmedBalanceSheetBranch(){Map<String,String> g=groups();g.put("Retention Amounts","Sundry Creditors");assertFalse(RetentionGroupHierarchy.isRetentionLedger("Retention Amounts","Retention Amounts",g));}
 @Test void missingOrCyclicHierarchyDoesNotGuess(){Map<String,String> g=groups();g.put("Project A","Project B");g.put("Project B","Project A");assertFalse(RetentionGroupHierarchy.isRetentionLedger("Project A","Retention Amounts",g));assertFalse(RetentionGroupHierarchy.isRetentionLedger("Project A","Missing",g));}
}
