USE [BRNET_TEST]
GO
/****** Object:  StoredProcedure [dbo].[proc_AgencyPostingWC]    Script Date: 11/3/2020 1:52:49 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

ALTER PROCEDURE [dbo].[proc_AgencyPostingWC]
(
	@short_code			Varchar(15),
	@ProcessCode		Varchar(2),
	@CustomerAccount	Varchar(15),
	@Amount				Money,
	@Reversal			Bit = 0,
	@DepositorNames     varchar(200),
	@AgentAccount		Varchar(15),
	@ChargeAmount		Money,
	@Stan               varchar(max),
	@AgentCommission	Money,
	@UcbCommission      Money
)

AS

DECLARE
	@AgentBranchID		Varchar(3),
	@AgentProductID		Varchar(6),
	@WorkingDate		DateTime,
	@DRTrxDescription	Varchar(100),
	@CRTrxDescription	Varchar(100),
	@ProductID			Varchar(6),
	@OtherProductID		vARCHAR(6), -- INCASE OF TRANSFER
	@OurBranchID		Varchar(3),
	@OtherBranch		Varchar(3), -- incase of transfer
	@SerialID			Varchar(10),
	@CurrencyID			Varchar(5),
	@TrxBatchID			Varchar(9),
	@MainRowID			bigint,
	@AccBalance			Money,
	@AgentBalance		Money,
	@CustIBGL			Varchar(15),
	@AgentIBGL			Varchar(15),
	@ExpenseGL			Varchar(15),
	@Status				int,
	@ErrorNo			Int,
	@IsBlocked			Bit,
	@IsDormant			Bit,
	@isCTrxAllow		Bit,
	@isATrxAllow		Bit,
	@AccountStatusID	Varchar(2),
	@AccountCurrency	Varchar(5),
	@ProductTypeID		Varchar(2),
	@MinimumBalance		Money,
	@CustomerNames varchar(100)


	CREATE Table #response
	(
		Field1		Varchar(2),
		Field2		Varchar(200)
	)

	SELECT @AgentBranchID=OurBranchID from t_AccountCustomer WHERE AccountID=@AgentAccount

	SELECT @OurBranchID = Ourbranchid FROM t_AccountCustomer WHERE Accountid = @CustomerAccount

	SELECT @AgentProductID = ProductID, @agentBranchid = OurBranchID FROM t_AccountCustomer WHERE AccountID = @AgentAccount
	SELECT @WorkingDate = SODDate FROM t_systemBranchStatus
	SELECT @CurrencyID = 'TSH'
	SELECT @isCTrxAllow = IsTrxAllow FROM t_SystemBranchStatus Where OurBranchid = @OurBranchID
	SELECT @isATrxAllow = IsTrxAllow FROM t_SystemBranchStatus Where OurBranchid = @agentBranchid

	SELECT @CustomerNames=Name FROM t_AccountCustomer where AccountID=@CustomerAccount

	IF @isCTrxAllow = 0 OR @isATrxAllow = 0
	BEGIN
		INSERT INTO #response
		VALUES('01', 'Transactions Not Allowed.')

		SELECT * FROM #response
		RETURN
	END
	ELSE
	IF ISNULL(@OurBranchID,'') = ''
	BEGIN
		-- Customer Does not Exist
		INSERT INTO #response
		VALUES('01', 'Customer Does Not Exist.')

		SELECT * FROM #response
		RETURN
	RETURN
	END

	ELSE

	BEGIN

	IF @ProcessCode = '01' -- Cash Withdrwal
	BEGIN
		SELECT @IsBlocked = IsBlocked, @IsDormant = IsDormant, @ProductID = ProductID, @AccountStatusID = AccountStatusID
		FROM t_AccountCustomer(NOLOCK)
		WHERE OurBranchID = @OurBranchID AND AccountID = @CustomerAccount

		SELECT @AccBalance = dbo.f_GetAvailableBalance (@OurBranchID,@CustomerAccount)

		SELECT @AccountCurrency = Currencyid, @ProductTypeID = ProductTypeID FROM t_Product WHERE productid = @productid
		SELECT @MinimumBalance = MinimumBalance FROM t_ProductBranchDetail WHERE productid = @productid

		IF (@AccBalance) < (@Amount + @ChargeAmount) --
		BEGIN
			INSERT INTO #Response
			VALUES('01','InSufficient Balance.')


			SELECT * FROM #Response order by Field1
			RETURN
		END ELSE

		IF @IsBlocked = 1 OR @IsDormant = 1 OR @AccountStatusID <> 'AA'
		BEGIN
			INSERT INTO #Response
			VALUES('01','Account Is Not Active.')

			SELECT * FROM #Response order by Field1
			RETURN
		END ELSE

		IF @AccountCurrency <> 'TSH'
		BEGIN
			INSERT INTO #Response
			VALUES('01','Invalid Currency.')

			SELECT * FROM #Response order by Field1
			RETURN
		END ELSE

		IF @ProductTypeID NOT IN ('CA','SB')
		BEGIN
			INSERT INTO #Response
			VALUES('01','Invalid Product.')

			SELECT * FROM #Response order by Field1
			RETURN
		END ELSE

		BEGIN

		SET @DRTrxDescription = 'WITHD : ~' + @short_code+' ~ '+@AgentAccount+'~'+@Stan
		SET @CRTrxDescription = 'WITHD :~' + @short_code+' ~ '+@CustomerAccount+'~'+@Stan

		IF @OurBranchID = @AgentBranchID
		BEGIN
		-- Debit Customer
			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @OurBranchID,
					@PAccountTypeID		= 'C',
					@PAccountID			= @CustomerAccount,
					@PProductID			= @ProductID,
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TD',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @Amount,
					@PLocalAmount		= @Amount,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @Amount,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '008',
					@PTrxDescription	= @DrTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT

			-- Credit Agent
			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @agentBranchid,
					@PAccountTypeID		= 'C',
					@PAccountID			= @AgentAccount,
					@PProductID			= @AgentProductID,
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TC',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @Amount,
					@PLocalAmount		= @Amount,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @Amount,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '007',
					@PTrxDescription	= @CRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT

		END
		ELSE
		BEGIN

		SELECT @CustIBGL = AccountID FROM t_GLInterBranch where Ourbranchid = @OurBranchID AND AccountTagID = 'IB_PBLE_AC'
		SELECT @AgentIBGL = AccountID FROM t_GLInterBranch where Ourbranchid = @agentBranchid AND AccountTagID = 'IB_PBLE_AC'

		SELECT @CustIBGL = '10360135'
		SELECT @AgentIBGL = '10360135'
					-- Debit Customer
			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @OurBranchID,
					@PAccountTypeID		= 'C',
					@PAccountID			= @CustomerAccount,
					@PProductID			= @ProductID,
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TD',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @Amount,
					@PLocalAmount		= @Amount,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @Amount,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '008',
					@PTrxDescription	= @DrTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT


			--Credit customer IB (Credit Interbranch B)
			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @OurBranchID,
					@PAccountTypeID		= 'G',
					@PAccountID			= @custIBGL,
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TC',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @Amount,
					@PLocalAmount		= @Amount,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @Amount,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '007',
					@PTrxDescription	= @DrTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT

					-- Debit Agent IB (Debit Interbranch A)
			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @agentBranchid,
					@PAccountTypeID		= 'G',
					@PAccountID			= @AgentIBGL,
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'CD',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @Amount,
					@PLocalAmount		= @Amount,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @Amount,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '008',
					@PTrxDescription	= @CRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT

			-- Credit Agent
			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @agentBranchid,
					@PAccountTypeID		= 'C',
					@PAccountID			= @AgentAccount,
					@PProductID			= @AgentProductID,
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'CC',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @Amount,
					@PLocalAmount		= @Amount,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @Amount,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '007',
					@PTrxDescription	= @CRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT



			INSERT INTO #response
			VALUES('01','00')
		END

		--- Withdrawal charges
		IF @ChargeAmount > 0
		BEGIN
			SELECT @DRTrxDescription = 'WITHD Charges: ~' + @short_code+' ~ '+@AgentAccount+'~'+@Stan
			SELECT @CRTrxDescription = 'WITHD Commission: ~' + @short_code+' ~ '+@CustomerAccount+'~'+@Stan
			IF @AgentBranchID = @OurBranchID  --INTRA
			BEGIN
			--Debit customer Full amount
				EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @OurBranchId,
					@PAccountTypeID		= 'C',
					@PAccountID			= @CustomerAccount,
					@PProductID			= @ProductID,
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TD',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @ChargeAmount,
					@PLocalAmount		= @ChargeAmount,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @ChargeAmount,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '008',
					@PTrxDescription	= @DRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT



					-- CREDIT BANK COMMISSION GL
				EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @OurBranchID,
					@PAccountTypeID		= 'G',
					@PAccountID			= '30600140',
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TC',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @UcbCommission,
					@PLocalAmount		= @UcbCommission,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @UcbCommission,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '007',
					@PTrxDescription	= @CRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT


						-- CREDIT AGENT AGENCY_PAYABLE GL
				EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @agentBranchid,
					@PAccountTypeID		= 'G',
					@PAccountID			= '22500606',
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TC',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @AgentCommission,
					@PLocalAmount		= @AgentCommission,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @AgentCommission,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '007',
					@PTrxDescription	= @CRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT

					INSERT INTO #response
			VALUES('01','00')

			END
			ELSE --INTERBRANCH
			BEGIN
			SELECT @CustIBGL = AccountID FROM t_GLInterBranch where Ourbranchid = @OurBranchID AND AccountTagID = 'IB_PBLE_AC'
			SELECT @AgentIBGL = AccountID FROM t_GLInterBranch where Ourbranchid = @agentBranchid AND AccountTagID = 'IB_PBLE_AC'
			SELECT @CustIBGL = '10360135'
			SELECT @AgentIBGL = '10360135'

				--Debit customer Full amount
				EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @ourBranchid,
					@PAccountTypeID		= 'C',
					@PAccountID			= @CustomerAccount,
					@PProductID			= @ProductID,
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TD',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @ChargeAmount,
					@PLocalAmount		= @ChargeAmount,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @ChargeAmount,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '008',
					@PTrxDescription	= @DRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT


				-- CREDIT BANK COMMISSION GL
				EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @ourBranchid,
					@PAccountTypeID		= 'G',
					@PAccountID			= '30600140',
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TC',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @UcbCommission,
					@PLocalAmount		= @UcbCommission,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @UcbCommission,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '007',
					@PTrxDescription	= @CRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT


-- Debit Customer
			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @OurBranchID,
					@PAccountTypeID		= 'G',
					@PAccountID			= @AgentIBGL,
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TC',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @AgentCommission,
					@PLocalAmount		= @AgentCommission,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @AgentCommission,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '008',
					@PTrxDescription	= @CRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT


			-- Debit Agent IB
			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @agentBranchid,
					@PAccountTypeID		= 'G',
					@PAccountID			= @AgentIBGL,
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'CD',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @AgentCommission,
					@PLocalAmount		= @AgentCommission,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @AgentCommission,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '008',
					@PTrxDescription	= @CRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT


						-- CREDIT AGENCY_PAYABLE GL
				EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @agentBranchid,
					@PAccountTypeID		= 'G',
					@PAccountID			= '22500606',
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'CC',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @AgentCommission,
					@PLocalAmount		= @AgentCommission,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @AgentCommission,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '007',
					@PTrxDescription	= @CRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT

					INSERT INTO #response
			VALUES('01','00')

			END
		END
		END

	END

	IF @ProcessCode = '02' -- Cash Deposit
	BEGIN
		SELECT @IsBlocked = IsBlocked, @IsDormant = IsDormant, @ProductID = ProductID, @AccountStatusID = AccountStatusID
		 FROM t_AccountCustomer(NOLOCK) WHERE OurBranchID = @OurBranchID AND AccountID = @CustomerAccount
		--SELECT @AgentBalance = ClearBalance FROM t_AccountCustomer(NOLOCK) WHERE OurBranchID = @agentBranchid AND AccountID = @AgentAccount
		SELECT @AgentBalance = dbo.f_GetAvailableBalance (@agentBranchid,@AgentAccount)

		SELECT @AccountCurrency = Currencyid, @ProductTypeID = ProductTypeID FROM t_Product WHERE productid = @productid

		IF @AgentBalance < @Amount
		BEGIN
			INSERT INTO #Response
			VALUES('01','Insufficient Agent Balance.')


			SELECT* FROM #Response order by Field1
			RETURN
		END ELSE

		IF  @IsBlocked = 1 OR @IsDormant = 1 OR @AccountStatusID <> 'AA'
		BEGIN
			INSERT INTO #Response
			VALUES('01','Account Not Active.')


			SELECT * FROM #Response order by Field1
			RETURN
		END ELSE
		IF @AccountCurrency <> 'TSH'
		BEGIN
			INSERT INTO #Response
			VALUES('01','Invalid Currency.')


			SELECT * FROM #Response order by Field1
			RETURN
		END ELSE

		IF @ProductTypeID NOT IN ('CA','SB')
		BEGIN
			INSERT INTO #Response
			VALUES('01','Invalid Product.')


			SELECT * FROM #Response order by Field1
			RETURN
		END ELSE

		BEGIN

			SET @DRTrxDescription = 'DEP: ~ ' +@DepositorNames+' ~ '+@CustomerAccount+' ~ '+@short_code+' ~ '+@Stan
			SET @CRTrxDescription = 'DEP: ~ ' +@DepositorNames+' ~ '+@AgentAccount+' ~ '+@short_code+' ~ '+@Stan


			IF @OurBranchID = @AgentBranchID
			BEGIN


			-- Debit Agent
			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @agentBranchid,
					@PAccountTypeID		= 'C',
					@PAccountID			= @AgentAccount,
					@PProductID			= @AgentProductID,
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TD',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @Amount,
					@PLocalAmount		= @Amount,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @Amount,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '008',
					@PTrxDescription	= @DRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT
																																																																																		BEGIN
		-- Credit Customer
			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @OurBranchID,
					@PAccountTypeID		= 'C',
					@PAccountID			= @CustomerAccount,
					@PProductID			= @ProductID,
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TC',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @Amount,
					@PLocalAmount		= @Amount,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @Amount,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '007',
					@PTrxDescription	= @CrTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT



					--- DEBIT EXPENSE

				EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @agentBranchid,
					@PAccountTypeID		= 'G',
					@PAccountID			= '40200196',
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TD',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @AgentCommission,
					@PLocalAmount		= @AgentCommission,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @AgentCommission,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '008',
					@PTrxDescription	= @DrTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT

			---Credit AGENCY_BANKING_PAYABLE

			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @OurBranchID,
					@PAccountTypeID		= 'G',
					@PAccountID			= '22500606',
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TC',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @AgentCommission,
					@PLocalAmount		= @AgentCommission,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @AgentCommission,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '007',
					@PTrxDescription	= @DRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT

			INSERT INTO #response
			VALUES('01','00')
			END
		END
			ELSE
			BEGIN

			SELECT @CustIBGL = AccountID FROM t_GLInterBranch where Ourbranchid = @OurBranchID AND AccountTagID = 'IB_PBLE_AC'
			SELECT @AgentIBGL = AccountID FROM t_GLInterBranch where Ourbranchid = @agentBranchid AND AccountTagID = 'IB_PBLE_AC'
			SELECT @CustIBGL = '10360135'
			SELECT @AgentIBGL = '10360135'


				-- Debit Agent
			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @agentBranchid,
					@PAccountTypeID		= 'C',
					@PAccountID			= @AgentAccount,
					@PProductID			= @AgentProductID,
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TD',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @Amount,
					@PLocalAmount		= @Amount,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @Amount,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '008',
					@PTrxDescription	= @DRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT

			-- Credit Agent IB (Credit Interbranch A)
				EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @agentBranchid,
					@PAccountTypeID		= 'G',
					@PAccountID			= @AgentIBGL,
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TC',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @Amount,
					@PLocalAmount		= @Amount,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @Amount,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '007',
					@PTrxDescription	= @CRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT


				--Debit customer IB (Debit Interbranch B)
				EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @OurBranchID,
					@PAccountTypeID		= 'G',
					@PAccountID			= @custIBGL,
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'CD',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @Amount,
					@PLocalAmount		= @Amount,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @Amount,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '008',
					@PTrxDescription	= @DrTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT


				-- Credit Customer
				EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @OurBranchID,
					@PAccountTypeID		= 'C',
					@PAccountID			= @CustomerAccount,
					@PProductID			= @ProductID,
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'CC',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @Amount,
					@PLocalAmount		= @Amount,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @Amount,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '007',
					@PTrxDescription	= @crTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT


					--- DEBIT EXPENSE (Agent Branch)

				EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @agentBranchid,
					@PAccountTypeID		= 'G',
					@PAccountID			= '40200196',
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TD',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @AgentCommission,
					@PLocalAmount		= @AgentCommission,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @AgentCommission,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '008',
					@PTrxDescription	= @DrTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT


-----Credit Interbranch Commission Expense (Agent Branch)

			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @agentBranchid,
					@PAccountTypeID		= 'G',
					@PAccountID			= '40200196',
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TC',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @AgentCommission,
					@PLocalAmount		= @AgentCommission,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @AgentCommission,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '007',
					@PTrxDescription	= @CRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT


			-----Debit Interbranch Commission Expense (Customer Branch)

			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @OurBranchID,
					@PAccountTypeID		= 'G',
					@PAccountID			= '40200196',
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TD',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @AgentCommission,
					@PLocalAmount		= @AgentCommission,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @AgentCommission,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '007',
					@PTrxDescription	= @CRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT

			-----Credit AGENCY_BANKING_PAYABLE

			EXEC p_InsertTransactions
					@PTrxBranchID		= @agentBranchid,
					@PTrxRowID			= @MainRowID OUTPUT,
					@PTrxBatchID		= @TrxBatchID OUTPUT,
					@PSerialID			= @SerialID OUTPUT,
					@POurBranchID		= @OurBranchID,
					@PAccountTypeID		= 'G',
					@PAccountID			= '22500606',
					@PProductID			= 'GL',
					@PModuleID			= 3020,
					@PTrxCodeID			= 0,
					@PTrxTypeID			= 'TC',
					@PTrxDate			= @WorkingDate,
					@PValueDate			= @WorkingDate,
					@PAmount			= @AgentCommission,
					@PLocalAmount		= @AgentCommission,
					@PTrxCurrencyID		= @CurrencyID,
					@PTrxAmount			= @AgentCommission,
					@PExchangeRate		= 1,
					@PMeanRate			= 1,
					@PProfit			= 0,
					@PInstrumentTypeID	= 'V',
					@PChequeID			= 0,
					@PChequeDate		= NULL,
					@PReferenceNo		= NULL,
					@PRemarks			= NULL,
					@PTrxDescriptionID	= '007',
					@PTrxDescription	= @CRTrxDescription,
					--@PMainGLID			= @SelcomGLAccountID,
					@PContraGLID		= NULL,
					@PTrxFlagID			= '',
					@PImageID			= 0,
					@PTrxPrinted		= 0,
					@PIsTrxPending		= 0,
					@PForwardRemark		= NULL,
					@PCreatedBy			= 'AGENT',
					@ErrorNo			= @ErrorNo OUTPUT

---Credit Agent the Deposit Commission from Expense GL

					--EXEC p_InsertTransactions
					--@PTrxBranchID		= @agentBranchid,
					--@PTrxRowID			= @MainRowID OUTPUT,
					--@PTrxBatchID		= @TrxBatchID OUTPUT,
					--@PSerialID			= @SerialID OUTPUT,
					--@POurBranchID		= @agentBranchid,
					--@PAccountTypeID		= 'C',
					--@PAccountID			= @AgentAccount,
					--@PProductID			= @AgentProductID,
					--@PModuleID			= 3020,
					--@PTrxCodeID			= 0,
					--@PTrxTypeID			= 'TC',
					--@PTrxDate			= @WorkingDate,
					--@PValueDate			= @WorkingDate,
					--@PAmount			= @AgentCommission,
					--@PLocalAmount		= @AgentCommission,
					--@PTrxCurrencyID		= @CurrencyID,
					--@PTrxAmount			= @AgentCommission,
					--@PExchangeRate		= 1,
					--@PMeanRate			= 1,
					--@PProfit			= 0,
					--@PInstrumentTypeID	= 'V',
					--@PChequeID			= 0,
					--@PChequeDate		= NULL,
					--@PReferenceNo		= NULL,
					--@PRemarks			= NULL,
					--@PTrxDescriptionID	= '007',
					--@PTrxDescription	= @CRTrxDescription,
					----@PMainGLID			= @SelcomGLAccountID,
					--@PContraGLID		= NULL,
					--@PTrxFlagID			= '',
					--@PImageID			= 0,
					--@PTrxPrinted		= 0,
					--@PIsTrxPending		= 0,
					--@PForwardRemark		= NULL,
					--@PCreatedBy			= 'AGENT',
					--@ErrorNo			= @ErrorNo OUTPUT

					INSERT INTO #response
			        VALUES('01','00')

			END

		END
	END


	END

		SELECT * FROM #response

