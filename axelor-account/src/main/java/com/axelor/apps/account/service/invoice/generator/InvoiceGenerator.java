/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.account.service.invoice.generator;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.AccountingSituation;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.InvoiceLineTax;
import com.axelor.apps.account.db.PaymentCondition;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.AccountConfigRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.AccountingSituationService;
import com.axelor.apps.account.service.JournalService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.app.AppAccountServiceImpl;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.InvoiceToolService;
import com.axelor.apps.account.service.invoice.generator.tax.TaxInvoiceLine;
import com.axelor.apps.account.service.payment.PaymentModeService;
import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.TradingNameService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ContextEntity;

public abstract class InvoiceGenerator  {
	

	private final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	protected JournalService journalService;

	protected int operationType;
	protected Company company;
	protected PaymentCondition paymentCondition;
	protected PaymentMode paymentMode;
	protected Address mainInvoicingAddress;
	protected Partner partner;
	protected Partner contactPartner;
	protected Currency currency;
	protected LocalDate today;
	protected PriceList priceList;
	protected String internalReference;
	protected String externalReference;
	protected Boolean inAti;
	protected BankDetails companyBankDetails;
	protected static int DEFAULT_INVOICE_COPY = 1;

	protected InvoiceGenerator(int operationType, Company company, PaymentCondition paymentCondition, PaymentMode paymentMode, Address mainInvoicingAddress,
			Partner partner, Partner contactPartner, Currency currency, PriceList priceList, String internalReference, String externalReference, Boolean inAti, 
			BankDetails companyBankDetails) throws AxelorException {

		this.operationType = operationType;
		this.company = company;
		this.paymentCondition = paymentCondition;
		this.paymentMode = paymentMode;
		this.mainInvoicingAddress = mainInvoicingAddress;
		this.partner = partner;
		this.contactPartner = contactPartner;
		this.currency = currency;
		this.priceList = priceList;
		this.internalReference = internalReference;
		this.externalReference = externalReference;
		this.inAti = inAti;
		this.companyBankDetails = companyBankDetails;
		this.today = Beans.get(AppAccountService.class).getTodayDate();
		this.journalService = new JournalService();

	}


	/**
	 * PaymentCondition, Paymentmode, MainInvoicingAddress, Currency récupérés du tiers
	 * @param operationType
	 * @param company
	 * @param partner
	 * @param contactPartner
	 * @throws AxelorException
	 */
	protected InvoiceGenerator(int operationType, Company company, Partner partner, Partner contactPartner, PriceList priceList,
			String internalReference, String externalReference, Boolean inAti) throws AxelorException {

		this.operationType = operationType;
		this.company = company;
		this.partner = partner;
		this.contactPartner = contactPartner;
		this.priceList = priceList;
		this.internalReference = internalReference;
		this.externalReference = externalReference;
		this.inAti = inAti;
		this.today = Beans.get(AppAccountService.class).getTodayDate();
		this.journalService = new JournalService();

	}


	protected InvoiceGenerator() {
		this.today = Beans.get(AppAccountService.class).getTodayDate();
		this.journalService = new JournalService();

	}

	protected int inverseOperationType(int operationType) throws AxelorException  {

		switch(operationType)  {

			case InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE:
				return InvoiceRepository.OPERATION_TYPE_SUPPLIER_REFUND;
			case InvoiceRepository.OPERATION_TYPE_SUPPLIER_REFUND:
				return InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE;
			case InvoiceRepository.OPERATION_TYPE_CLIENT_SALE:
				return InvoiceRepository.OPERATION_TYPE_CLIENT_REFUND;
			case InvoiceRepository.OPERATION_TYPE_CLIENT_REFUND:
				return InvoiceRepository.OPERATION_TYPE_CLIENT_SALE;
			default:
				throw new AxelorException(TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get(IExceptionMessage.INVOICE_GENERATOR_1), AppAccountServiceImpl.EXCEPTION);
		}

	}

	
	abstract public Invoice generate() throws AxelorException;


	protected Invoice createInvoiceHeader() throws AxelorException  {

		Invoice invoice = new Invoice();
		
		invoice.setCompany(company);

		invoice.setOperationTypeSelect(operationType);

		if (partner == null) {
			throw new AxelorException(TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get(IExceptionMessage.INVOICE_GENERATOR_2), AppAccountServiceImpl.EXCEPTION);
		}
		invoice.setPartner(partner);

		AccountingSituation accountingSituation = Beans
				.get(AccountingSituationService.class)
				.getAccountingSituation(partner, company);
		if (accountingSituation != null) {
		    invoice.setInvoiceAutomaticMail(accountingSituation.getInvoiceAutomaticMail());
		    invoice.setInvoiceMessageTemplate(accountingSituation.getInvoiceMessageTemplate());
		}

		if (paymentCondition == null) {
			paymentCondition = InvoiceToolService.getPaymentCondition(invoice);
		}
		if (paymentCondition == null) {
			throw new AxelorException(TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get(IExceptionMessage.INVOICE_GENERATOR_3), AppAccountServiceImpl.EXCEPTION);
		}
		invoice.setPaymentCondition(paymentCondition);

		if (paymentMode == null) {
			paymentMode = InvoiceToolService.getPaymentMode(invoice);
		}
		if (paymentMode == null) {
			throw new AxelorException(TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get(IExceptionMessage.INVOICE_GENERATOR_4), AppAccountServiceImpl.EXCEPTION);
		}
		invoice.setPaymentMode(paymentMode);

		if (mainInvoicingAddress == null) {
			mainInvoicingAddress = Beans.get(PartnerService.class).getInvoicingAddress(partner);
		}
		if (mainInvoicingAddress == null && partner.getIsCustomer()) {
			throw new AxelorException(TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get(IExceptionMessage.INVOICE_GENERATOR_5), AppAccountServiceImpl.EXCEPTION);
		}

		invoice.setAddress(mainInvoicingAddress);

		invoice.setContactPartner(contactPartner);

		if (currency == null) {
			currency = partner.getCurrency();
		}
		if (currency == null) {
			throw new AxelorException(TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get(IExceptionMessage.INVOICE_GENERATOR_6), AppAccountServiceImpl.EXCEPTION);
		}
		invoice.setCurrency(currency);

		invoice.setStatusSelect(InvoiceRepository.STATUS_DRAFT);

		invoice.setPriceList(priceList);

		invoice.setInternalReference(internalReference);

		invoice.setExternalReference(externalReference);

		invoice.setPrintingSettings(Beans.get(TradingNameService.class)
				.getDefaultPrintingSettings(null, company));

		// Set ATI mode on invoice
		AccountConfigService accountConfigService = Beans.get(AccountConfigService.class);
		AccountConfig accountConfig = accountConfigService.getAccountConfig(company);
		
		int atiChoice = accountConfig.getInvoiceInAtiSelect();
		
		if (inAti == null) {
			invoice.setInAti(accountConfigService.getInvoiceInAti(accountConfig));
		} else if (atiChoice == AccountConfigRepository.INVOICE_ATI_DEFAULT || atiChoice == AccountConfigRepository.INVOICE_WT_DEFAULT) {
			invoice.setInAti(inAti);
		} else if (atiChoice == AccountConfigRepository.INVOICE_ATI_ALWAYS) {
			invoice.setInAti(true);
		} else {
			invoice.setInAti(false);
		}
		
		// Set Company bank details
		if(companyBankDetails == null)  {
			if(accountingSituation != null)  {
				if(partner.getOutPaymentMode().equals(paymentMode)) {
					companyBankDetails = accountingSituation.getCompanyOutBankDetails();
				}
				else if (partner.getInPaymentMode().equals(paymentMode)) {
					companyBankDetails = accountingSituation.getCompanyInBankDetails();
				}
			}
			if(companyBankDetails == null)  {
				companyBankDetails = company.getDefaultBankDetails();
				List<BankDetails> allowedBDs = Beans.get(PaymentModeService.class).
						getCompatibleBankDetailsList(paymentMode, company);
				if (!allowedBDs.contains(companyBankDetails)) {
				    companyBankDetails = null;
				}
			}
		}
		invoice.setCompanyBankDetails(companyBankDetails);
		
		invoice.setInvoicesCopySelect(getInvoiceCopy());
		
		initCollections(invoice);

		return invoice;
	}



	public int getInvoiceCopy() {
		if(partner.getIsCustomer()) {
			return partner.getInvoicesCopySelect();
		} 
		return DEFAULT_INVOICE_COPY;
	}

	/**
	 * Peupler une facture.
	 * <p>
	 * Cette fonction permet de déterminer de déterminer les tva d'une facture à partir des lignes de factures  en paramètres.
	 * </p>
	 *
	 * @param invoice
	 * @param invoiceLines
	 *
	 * @throws AxelorException
	 */
	public void populate(Invoice invoice, List<InvoiceLine> invoiceLines) throws AxelorException {

		logger.debug("Peupler une facture => lignes de factures: {} ", new Object[] {  invoiceLines.size() });

		initCollections( invoice );

		// Create tax lines.
        List<InvoiceLineTax> invoiceTaxLines = (new TaxInvoiceLine(invoice, invoiceLines)).creates();

        // Workaround for #9759
	    if (invoice instanceof ContextEntity) {
	        invoice.getInvoiceLineList().addAll(invoiceLines);
	        invoice.getInvoiceLineTaxList().addAll(invoiceTaxLines);
	    } else {
	        invoiceLines.stream().forEach(invoice::addInvoiceLineListItem);
	        invoiceTaxLines.stream().forEach(invoice::addInvoiceLineTaxListItem);
	    }

		computeInvoice(invoice);

	}


	/**
	 * Initialiser l'ensemble des Collections d'une facture
	 *
	 * @param invoice
	 */
	protected void initCollections(Invoice invoice){

		initInvoiceLineTaxList(invoice);
		initInvoiceLineList(invoice);

	}

	/**
	 * Initialiser l'ensemble des listes de ligne de facture d'une facture
	 *
	 * @param invoice
	 */
	protected void initInvoiceLineList(Invoice invoice) {

		if (invoice.getInvoiceLineList() == null) { invoice.setInvoiceLineList(new ArrayList<InvoiceLine>()); }
		else  {  invoice.getInvoiceLineList().clear();  }

	}


	/**
	 * Initiate the list of invoice tax lines
	 *
	 * @param invoice
	 */
	protected void initInvoiceLineTaxList(Invoice invoice) {

		if (invoice.getInvoiceLineTaxList() == null) { invoice.setInvoiceLineTaxList(new ArrayList<InvoiceLineTax>()); }
		else { invoice.getInvoiceLineTaxList().clear(); }

	}

	/**
	 * Compute the invoice total amounts
	 *
	 * @param invoice
	 * @throws AxelorException
	 */
	public void computeInvoice(Invoice invoice) throws AxelorException {

		// In the invoice currency
		invoice.setExTaxTotal( BigDecimal.ZERO );
		invoice.setTaxTotal( BigDecimal.ZERO );
		invoice.setInTaxTotal( BigDecimal.ZERO );

		// In the company accounting currency
		invoice.setCompanyExTaxTotal(BigDecimal.ZERO);
		invoice.setCompanyTaxTotal(BigDecimal.ZERO);
		invoice.setCompanyInTaxTotal(BigDecimal.ZERO);
		
		for(InvoiceLine invoiceLine : invoice.getInvoiceLineList())  {
			// In the invoice currency
			invoice.setExTaxTotal(invoice.getExTaxTotal().add( invoiceLine.getExTaxTotal() ));
			
			// In the company accounting currency
			invoice.setCompanyExTaxTotal(invoice.getCompanyExTaxTotal().add( invoiceLine.getCompanyExTaxTotal() ));
		}

		for (InvoiceLineTax invoiceLineTax : invoice.getInvoiceLineTaxList()) {

			// In the invoice currency
			invoice.setTaxTotal(invoice.getTaxTotal().add( invoiceLineTax.getTaxTotal() ));

			// In the company accounting currency
			invoice.setCompanyTaxTotal(invoice.getCompanyTaxTotal().add( invoiceLineTax.getCompanyTaxTotal() ));

		}
		
		// In the invoice currency
		invoice.setInTaxTotal(invoice.getExTaxTotal().add( invoice.getTaxTotal() ));

		// In the company accounting currency
		invoice.setCompanyInTaxTotal(invoice.getCompanyExTaxTotal().add( invoice.getCompanyTaxTotal() ));

		invoice.setAmountRemaining(invoice.getInTaxTotal());
		invoice.setHasPendingPayments(false);
		
		logger.debug("Invoice amounts : W.T. = {}, Tax = {}, A.T.I. = {}",
			new Object[] { invoice.getExTaxTotal(), invoice.getTaxTotal(), invoice.getInTaxTotal() });

	}

}
