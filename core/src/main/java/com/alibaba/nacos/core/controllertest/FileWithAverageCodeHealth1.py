def main():
    # code here
    pass

if __name__ == "__main__":
    main()


def create_transaction_from_cart(cart: Cart) -> DbTransaction:
    """Create a transaction based on cart information"""
    res = DbTransaction(type=TransactionType.PURCHASE, currency=cart.currency, timestamp=now(),
                        total_amount=cart.total_amount, state=TransactionState.INITIALIZED, id=ObjectId())
    items = []
    vat: Dict[Decimal, Vat] = {}
    for item in cart.items:
        ps = item.product_set.fetch()
        t_item = TransactionItem(amount=item.amount, count=item.count,
                                 product_set_id=ps.full_id, product_set_title=item.product_set_title)
        if item.manual_activation is not None:
            t_item.manual_activation = item.manual_activation
        if item.mtb_product_owner is not None:
            t_item.mtb_product_owner = item.mtb_product_owner
        if item.mtb_bearer is not None:
            t_item.mtb_bearer = item.mtb_bearer
        if item.start_of_validity is not None:
            t_item.start_of_validity = item.start_of_validity
        items.append(t_item)
        for percentage, amount in ps.vat().items():
            amount *= item.count
            if percentage in vat:
                vat[percentage].amount += amount
            else:
                vat[percentage] = Vat(amount=amount, percentage=percentage)
    res.items = items
    res.vat = list(vat.values())
    res.discount_codes = list(cart.discount_codes)
    res.description = cart.name
    return res


def create_transaction_from_product_set(ps: ProductSet) -> DbTransaction:
    """Create a transaction based on cart information"""
    res = DbTransaction(type=TransactionType.PURCHASE, currency=ps.currency, timestamp=now(), total_amount=ps.amount,
                        state=TransactionState.INITIALIZED, id=ObjectId())
    res.items = [TransactionItem(amount=ps.amount, product_set_id=ps.full_id, product_set_title=ps.title)]
    res.vat = to_vat_list(ps.vat())
    res.description = ps.title
    return res


def get_db_transaction(transaction_id: str) -> DbTransaction:
    """Get transaction"""
    return DbTransaction.objects.get_or_404(id=transaction_id)


def get_transaction(identity: Identity, id: str) -> Transaction:
    """Get specified transaction"""
    return to_transaction_model(_update_cancellable(get_db_transaction(id)))


def get_transactions(identity: Identity, after: str = None, before: str = None, mtb_product_id: str = None,
                     traveller_id: str = None, vendor_id: str = None, wallet_id: str = None) -> List[Transaction]:
    """Get all transactions matching filter"""
    query = None
    if after:
        query = Q(timestamp__gte=after)
    if before:
        if query is None:
            query = Q(timestamp__lt=before)
        else:
            query = query & Q(timestamp__lt=before)
    if mtb_product_id:
        if query is None:
            query = Q(items__mtb_product_ids=mtb_product_id)
        else:
            query = query & Q(items__mtb_product_ids=mtb_product_id)
    if traveller_id:
        q = Q(owner=db_id(traveller_id)) | Q(recipient=db_id(traveller_id))
        if query is None:
            query = q
        else:
            query &= q
    if vendor_id:
        q = Q(owner=db_id(vendor_id)) | Q(recipient=db_id(vendor_id))
        if query is None:
            query = q
        else:
            query &= q
    if wallet_id:
        if query is None:
            query = Q(wallet=db_id(wallet_id))
        else:
            query = query & Q(wallet=db_id(wallet_id))
    return [to_transaction_model(_update_cancellable(trans)) for trans in (DbTransaction.objects(q_obj=query))]


def get_transaction_receipt(identity: Identity, transaction_id: str, email_receipt: bool = False, email: str = None) \
        -> Receipt:
    """Get receipt for transaction"""
    transaction = get_db_transaction(transaction_id)
    if transaction.state == TransactionState.PURCHASED:
        if email_receipt or email:
            if not _send_receipt_email(identity.user, transaction, email):
                abort(400, "No e-mail receipient specified")
        return transaction.to_models(Receipt, transaction_id=str(transaction.id))
    else:
        abort(404, "Transaction not completed")


def initialize_purchase_transaction(identity: Identity, transaction: DbTransaction,
                                    purchase: PurchaseRequest) -> Optional[str]:
    """Initialize purchase transaction"""
    if identity.user is None:
        abort(400, "Must be a vendor or traveller to initialize a purchase")
    if purchase.start_of_validity:
        if purchase.manual_activation:
            abort(400, "You can not specify both manual activation and start of validity")
        if purchase.start_of_validity < expire(-60):
            abort(400, "You can not specify start of validity in the past")
        # TODO check for start of valid values for start_of_validity
    mtb_bearer = get_db_mtb_bearer(identity, purchase.mtb_bearer_id, error_400=True) if purchase.mtb_bearer_id else None
    if purchase.mtb_product_owner:
        try:
            mtb_product_owner = User.get_from_owner_ref(purchase.mtb_product_owner)
        except DoesNotExist:
            abort(400, "Can't access specified mtbProductOwner ")
        if mtb_bearer is not None and mtb_bearer.owner.id != mtb_product_owner.id:
            abort(400, "Specified mtbBearerId must match specified mtbProductOwner")
    else:
        mtb_product_owner = None
    for item in transaction.items:
        if purchase.start_of_validity:
            if item.manual_activation:
                abort(400, "You can not specify manual activation in cart and start of validity in purchase")
            if item.start_of_validity is None:
                item.start_of_validity = purchase.start_of_validity
        elif purchase.manual_activation and item.manual_activation is None:
            item.manual_activation = purchase.manual_activation
        if mtb_bearer is not None:
            if item.mtb_bearer is None:
                item.mtb_bearer = mtb_bearer
        if mtb_product_owner is not None:
            if item.mtb_product_owner is None:
                item.mtb_product_owner = mtb_product_owner
        elif item.mtb_product_owner is None:
            if item.mtb_bearer is not None:
                item.mtb_product_owner = item.mtb_bearer.owner.fetch()
            else:
                item.mtb_product_owner = identity.user
        if item.mtb_bearer is not None and item.mtb_bearer.owner.id != item.mtb_product_owner.id:
            abort(400, "Specified mtbBearerId must match specified mtbProductOwner")
    transaction.owner = identity.user
    if identity.user.type == USER_TYPE_VENDOR:
        transaction.state = TransactionState.FINALIZE_PENDING
        transaction.save()
        webview_url = None
    else:
        webview_url = _initialize_traveller_purchase(identity, purchase, transaction)
    return webview_url


def _initialize_traveller_purchase(identity: Identity, purchase: PurchaseRequest,
                                   transaction: DbTransaction) -> Optional[str]:
    wallet = get_db_wallet(identity, purchase.wallet_id, error_400=True)
    if purchase.payment_method_id is not None:
        payment_method = get_wallet_payment_method(wallet, purchase.payment_method_id)
    else:
        payment_method = None
    if purchase.use_purse is None:
        if wallet.use_purse == USE_PURSE_OPTIONAL:
            use_purse = payment_method is None
        else:
            use_purse = wallet.use_purse == USE_PURSE_ALWAYS
    elif purchase.use_purse:
        if wallet.use_purse == USE_PURSE_NEVER:
            abort(400, "Wallet doesn't allow use of purse")
        use_purse = True
    else:
        if wallet.use_purse == USE_PURSE_ALWAYS:
            abort(400, "Wallet doesn't allow purchases without using purse")
        use_purse = False
    if use_purse:
        new_reservation = NewPurseReservation(amount=float(transaction.total_amount),
                                              partial=payment_method is not None)
        try:
            reservation = purse_create_reservation(wallet.purse_id, new_reservation)
            transaction.purse_reservation_id = reservation.id
            transaction.purse_amount = from_float_amount(reservation.amount, wallet.currency)
            transaction.expire = reservation.expire
        except ApiException as ae:
            transaction.purse_amount = Decimal(0)
            transaction.purse_reservation_id = None
            # TODO handle other than 409!?
            if ae.status == 409:
                logger.debug("No money in purse")
            else:
                logger.error("Failed to access purse, skipping")
    else:
        transaction.purse_amount = Decimal(0)
        transaction.purse_reservation_id = None
    if transaction.purse_amount < transaction.total_amount:
        if payment_method is not None:
            transaction.payment_method_id = payment_method.id
            transaction.payment_method_amount = transaction.total_amount - transaction.purse_amount
            psd = get_payment_service_driver(get_payment_service(payment_method.payment_service_id))
            tid = str(transaction.id)
            try:
                payment_transaction = \
                    psd.init_payment(payment_method, transaction.payment_method_amount, tid,
                                     transaction.description, transaction.currency,
                                     replace_var("TID", purchase.return_url, tid),
                                     get_best_lang(identity.user), purchase.mobile)
            except PaymentServiceException as pse:
                logger.error("Failed to initialize payment: %s", pse)
                raise pse
            pt_expire = payment_transaction.get_expire()
            if pt_expire is not None:
                if transaction.expire is None or pt_expire < transaction.expire:
                    transaction.expire = pt_expire
            transaction.payment_method_transaction_data = payment_transaction.save_dict()
        elif purchase.use_purse:
            abort(400, "Not enough funds available in purse for purchase")
        else:
            abort(400, "No payment method specified")
        webview_url = payment_transaction.get_webview_url()
    else:
        transaction.payment_method_id = None
        transaction.payment_method_transaction_data = None
        transaction.payment_method_amount = None
        webview_url = None
    transaction.wallet = wallet
    transaction.state = TransactionState.USER_INTERACTION_PENDING if webview_url is not None \
        else TransactionState.FINALIZE_PENDING
    transaction.save()
    return webview_url


def _finalize_traveller_purchase(auth_ctx: AuthorizationContext, transaction: DbTransaction, finalization_data: str) \
        -> None:
    """Finalize purchase transaction and create receipt"""
    if transaction.payment_method_transaction_data:
        payment_method = get_wallet_payment_method(transaction.wallet, transaction.payment_method_id)
        psd = get_payment_service_driver(get_payment_service(payment_method.payment_service_id))
        payment_transaction = psd.create_payment_service_transaction(transaction.payment_method_transaction_data)
        try:
            psd.reserve_payment(payment_transaction)
        except PaymentServiceException as pse:
            logger.error("Failed to reserve payment: %s", pse)
            transaction.state = TransactionState.CANCELLED if isinstance(pse, UserInteractionCanceledException) \
                else TransactionState.DENIED
            _release_purse_reservation(transaction)
            transaction.save()
            raise pse
    else:
        psd = None
        payment_transaction = None
    try:
        _finalize_create_mtb_products(transaction)
    except Exception as exc:
        logger.error("Failed to create product: %s", exc)
        transaction.state = TransactionState.ISSUE_ERROR
        _release_purse_reservation(transaction)
        _release_payment_method_reservation(transaction, psd, payment_transaction)
        _revert_mtb_products(auth_ctx, transaction)
        if payment_transaction:
            transaction.payment_method_transaction_data = payment_transaction.save_dict()
        transaction.save()
        raise exc
    if transaction.purse_reservation_id is not None:
        record = NewPurseRecord(transaction_id=str(transaction.id),
                                reservation_id=transaction.purse_reservation_id,
                                amount=-float(transaction.purse_amount),
                                refundable=False)
        try:
            res = purse_create_record(transaction.wallet.purse_id, record)
        except ApiException as ae:
            logger.error("Failed to record purse transaction: %s", ae)
            transaction.state = TransactionState.DENIED
            _revert_mtb_products(auth_ctx, transaction)
            _release_payment_method_reservation(transaction, psd, payment_transaction)
            if payment_transaction:
                transaction.payment_method_transaction_data = payment_transaction.save_dict()
            transaction.save()
            raise ae
        transaction.purse_record_ids.append(res.id)
        transaction.purse_reservation_id = None
    if transaction.payment_method_transaction_data:
        try:
            transaction.payment_reference = psd.finalize_payment(payment_transaction, finalization_data)
        except PaymentServiceException as pse:
            logger.error("Failed to reserve payment: %s", pse)
            transaction.state = TransactionState.DENIED
            _revert_mtb_products(auth_ctx, transaction)
            _revert_purse_record(transaction)
            if payment_transaction:
                transaction.payment_method_transaction_data = payment_transaction.save_dict()
            transaction.save()
            raise pse
    _mark_purchased(transaction)
    # TODO purge some, transaction.payment_method_transaction_data
    try:
        if payment_transaction:
            transaction.payment_method_transaction_data = payment_transaction.save_dict()
        transaction.save()
    except Exception as exc:
        # TODO finer error handling
        transaction.state = TransactionState.ERROR
        _revert_mtb_products(auth_ctx, transaction)
        if transaction.purse_record_ids:
            # TODO revert record?
            pass
        if transaction.payment_method_transaction_data is not None:
            # TODO release payment reservation/payment?
            pass
        transaction.save()
        raise exc


def _finalize_create_mtb_products(transaction):
    """Create MTB products and set cancellable state"""
    transaction.cancellable = True
    transaction.cancellable_expire = expire(shared.config.parameters.purchase.cancel_ttl_max)
    try:
        for item in transaction.items:
            item.mtb_product_ids = []
            for mp in create_mtb_products(item, transaction):
                item.mtb_product_ids.append(mp.id)
                if not mp.cancellable:
                    transaction.cancellable = False
                    transaction.cancellable_expire = None
                elif transaction.cancellable_expire and mp.cancellable_expire \
                        and transaction.cancellable_expire > mp.cancellable_expire:
                    transaction.cancellable_expire = mp.cancellable_expire
    except Exception as exc:
        # TODO remove mtb_products
        transaction.cancellable_expire = None
        transaction.cancellable = False
        raise exc


def _mark_purchased(transaction: DbTransaction) -> None:
    """Mark products and transaction as purchased"""
    transaction.state = TransactionState.PURCHASED
    for item in transaction.items:
        if item.mtb_product_ids:
            for mp_id in item.mtb_product_ids:
                try:
                    mtb_prod = get_db_mtb_product(None, mp_id, all=True, refresh=False)
                    mtb_prod.purchased = True
                    mtb_prod.save()
                except Exception as exc:
                    logger.error("Failed to mark mtb_product {mp_id} as purchase", exc_info=exc)


def _finalize_vendor_purchase(auth_ctx: AuthorizationContext, transaction: DbTransaction, finalization_data: str) \
        -> None:
    """Finalize vendor transaction and create receipt"""
    if not finalization_data:
        abort(400, "Vendor finalization requires payment reference")
    transaction.payment_reference = finalization_data
    try:
        _finalize_create_mtb_products(transaction)
    except Exception as exc:
        transaction.state = TransactionState.ISSUE_ERROR
        _revert_mtb_products(auth_ctx, transaction)
        transaction.save()
        raise exc
    _mark_purchased(transaction)
    transaction.save()


def finalize_transaction(auth_ctx: AuthorizationContext, transaction_id: str, payment_reference: str) -> DbTransaction:
    """Finalize transaction"""
    transaction = get_db_transaction(transaction_id)
    if transaction.type != TransactionType.PURCHASE:
        abort(400, "Currently only purchases can be finalized")
    transaction.assert_pending_state()
    if auth_ctx.identity.user.id != transaction.owner.id:
        abort("Finalize must be called by the transaction initiator")
    if auth_ctx.identity.user.type == USER_TYPE_VENDOR:
        _finalize_vendor_purchase(auth_ctx, transaction, payment_reference)
    else:
        _finalize_traveller_purchase(auth_ctx, transaction, payment_reference)
        _send_receipt_email(auth_ctx.identity.user, transaction)
    return transaction


def finalize_transaction_receipt(auth_ctx: AuthorizationContext, transaction_id: str,
                                 receipt_req: Dict[str, Any]) -> Receipt:
    """Finalize transaction and create receipt"""
    req = ReceiptRequest.from_dict(receipt_req)
    transaction = finalize_transaction(auth_ctx, transaction_id, req.payment_reference)
    return transaction.to_models(Receipt, transaction_id=str(transaction.id))


def update_transaction(auth_ctx: AuthorizationContext, transaction_id: str, transaction_update: Dict[str, Any]) \
        -> Transaction:
    """Update transaction and reservations"""
    transaction = get_db_transaction(transaction_id)
    update = TransactionInformation.from_dict(transaction_update)
    estate = TransactionState(update.state)
    if estate == TransactionState.CANCELLED:
        if transaction.is_cancellable(auth_ctx):
            if transaction.type == TransactionType.PURCHASE:
                cancel_payment_transaction(auth_ctx, transaction)
            elif transaction.type == TransactionType.LOAN:
                cancel_loan_transaction(auth_ctx, transaction)
            elif transaction.type == TransactionType.TRANSFER:
                cancel_transfer_transaction(auth_ctx, transaction)
            else:
                abort(500, f"Unknown transaction type {transaction.type}")
        else:
            abort(403, "Transaction isn't cancellable")
    elif estate == TransactionState.PURCHASED:
        transaction = finalize_transaction(auth_ctx.identity, transaction_id, update.payment_reference)
    else:
        abort(501, f"Unhandled state change to {update.state}")
    return to_transaction_model(transaction)


def cancel_payment_transaction(auth_ctx: AuthorizationContext, transaction: DbTransaction) -> None:
    """Cancel transaction and refund payments and invalidate products"""
    for item in transaction.items:
        if item.mtb_product_ids:
            for mp_id in item.mtb_product_ids:
                try:
                    prod = get_db_mtb_product(auth_ctx, mp_id)
                    check_mtb_product_useable(auth_ctx, prod)
                except AbortException:
                    abort(409, "Transaction contains lent products")
    state = transaction.state
    if state == TransactionState.PURCHASED or state == TransactionState.FINALIZE_PENDING \
            or state == TransactionState.USER_INTERACTION_PENDING:
        _refund_payment(transaction, "Cancel requested")
        _refund_purse(transaction)
    else:
        abort(501, f"Transaction in state {state} isn't cancellable")

    for item in transaction.items:
        if item.mtb_product_ids:
            for mp_id in item.mtb_product_ids:
                try:
                    cancel_mtb_product(auth_ctx, mp_id, transaction)
                except Exception as exc:
                    logger.error("Failed to cancel mtb_product {mp_id}", exc_info=exc)
                    # TODO remove product from traveller
    transaction.cancellable = False
    transaction.cancellable_expire = None
    transaction.state = TransactionState.CANCELLED
    transaction.save()
    # TODO error handling


def _refund_payment(transaction: DbTransaction, reason: str) -> None:
    """Try to refund payment, abort if failed.
    """
    if transaction.payment_method_amount:
        if transaction.payment_method_transaction_data:
            payment_method = get_wallet_payment_method(transaction.wallet, transaction.payment_method_id)
            psd = get_payment_service_driver(get_payment_service(payment_method.payment_service_id))
            payment_transaction = psd.create_payment_service_transaction(transaction.payment_method_transaction_data)
            try:
                psd.cancel_payment(payment_transaction, transaction.payment_reference, reason)
                logger.info("Transaction({transaction.id}) payment refunded OK")
                transaction.state = TransactionState.CANCEL_PAYMENT_REFUNDED
                transaction.save()
            except PaymentServiceException as pse:
                logger.error("Failed to cancel payment: %s", pse)
                transaction.state = TransactionState.CANCEL_FAILED
                transaction.save()
                raise pse
        else:
            abort(500, "Payment data missing")


def _refund_purse(transaction: DbTransaction) -> None:
    """Try to refund purse payment, set state to CANCEL_PURSE_PENDING if failed.
    """
    if transaction.purse_amount:
        records = []
        if not transaction.purse_refundable_amount:
            records.append(NewPurseRecord(transaction_id=str(transaction.id),
                                          amount=float(transaction.purse_amount),
                                          refundable=False))
        elif transaction.purse_refundable_amount == transaction.purse_amount:
            records.append(NewPurseRecord(transaction_id=str(transaction.id),
                                          amount=float(transaction.purse_amount),
                                          refundable=True))
        else:
            records.append(NewPurseRecord(transaction_id=str(transaction.id),
                                          amount=float(transaction.purse_refundable_amount),
                                          refundable=True))
            records.append(NewPurseRecord(transaction_id=str(transaction.id),
                                          amount=float(transaction.purse_amount - transaction.purse_refundable_amount),
                                          refundable=False))
        try:
            for record in records:
                res = purse_create_record(transaction.wallet.purse_id, record)
                transaction.purse_record_ids.append(res.id)
                transaction.save()
        except ApiException as ae:
            logger.error("Failed to record purse refund transaction: %s", ae)
            transaction.state = TransactionState.CANCEL_FAILED
            transaction.save()


def _release_purse_reservation(transaction: DbTransaction) -> None:
    """Release possible purse reservation"""
    if transaction.purse_reservation_id is not None:
        try:
            delete_reservation(transaction.wallet.purse_id, transaction.purse_reservation_id)
            transaction.purse_reservation_id = None
            transaction.save()
        except ApiException as ae:
            logger.error("Failed to delete purse reservation, purse=%s, reservation=%s",
                         transaction.wallet.purse_id, transaction.purse_reservation_id, exc_info=ae)


def _revert_purse_record(transaction: DbTransaction) -> None:
    """Revert possible purse record"""
    if transaction.purse_amount:
        records = []
        if not transaction.purse_refundable_amount:
            records.append(NewPurseRecord(transaction_id=str(transaction.id),
                                          amount=float(transaction.purse_amount),
                                          refundable=False))
        elif transaction.purse_refundable_amount == transaction.purse_amount:
            records.append(NewPurseRecord(transaction_id=str(transaction.id),
                                          amount=float(transaction.purse_amount),
                                          refundable=True))
        else:
            records.append(NewPurseRecord(transaction_id=str(transaction.id),
                                          amount=float(transaction.purse_refundable_amount),
                                          refundable=True))
            records.append(NewPurseRecord(transaction_id=str(transaction.id),
                                          amount=float(transaction.purse_amount - transaction.purse_refundable_amount),
                                          refundable=False))
        try:
            for record in records:
                res = purse_create_record(transaction.wallet.purse_id, record)
                transaction.purse_record_ids.append(res.id)
                transaction.save()
        except ApiException as ae:
            logger.error("Failed to record purse revert transaction: %s", ae)
            transaction.save()


def _release_payment_method_reservation(transaction: DbTransaction, psd: PaymentServiceDriver,
                                        payment_transaction: PaymentServiceTransaction) -> None:
    """Release possible payment method reservation"""
    if transaction.payment_method_amount:
        if transaction.payment_method_transaction_data:
            try:
                psd.cancel_payment(payment_transaction, transaction.payment_reference,
                                   f"Release payment because of {transaction.state}")
                logger.info("Transaction({transaction.id}) payment released OK")
            except PaymentServiceException as pse:
                logger.error("Failed to release payment: %s", pse)
        else:
            logger.error("Payment data missing, failed to release payment")


def _revert_mtb_products(auth_ctx: AuthorizationContext, transaction: DbTransaction) -> None:
    """Revert possible created products"""
    for item in transaction.items:
        for mtb_prod_id in item.mtb_product_ids:
            try:
                cancel_mtb_product(auth_ctx, mtb_prod_id, transaction)
            except Exception as exc:
                logger.error("Failed to cancel mtb_product {mtb_prod_id}", exc_info=exc)
        item.mtb_product_ids.clear()


def _send_receipt_email(user: User, transaction: DbTransaction, email: str = None) -> bool:
    """Send an e-mail with the a receipt for the transaction to the user.
        Return true if email address found.
    """
    if not email:
        email = user.receipt_email
    if email:
        locale = user.locale
        if locale is None:
            locale = shared.config.default_locale
        logger.debug("Send receipt in %s to %s", locale, user.receipt_email)
        try:
            t = Template(name='receipt_email', locale=locale, global_variables=shared.jinja_globals)
            template_env = {'transaction': transaction}
            subject = t.render(template_env, section='subject')
            message = t.render(template_env, section='body')
            content_type = t.content_type
        except TemplateNotFound:
            subject = "Receipt from BobCat"
            message = f"Receipt for transaction: {transaction.id}"
            content_type = 'text/plain'
        services.email_sender.send(email_from=None, email_to=email, subject=subject, message=message,
                                   content_type=content_type)
        return True
    else:
        return False


def _remove_id_from_optional_set(id: str, id_set: Optional[Set[str]]):
    """Remove id from set. Return True if removed or no set."""
    if id_set is None:
        return True
    try:
        id_set.remove(id)
        return True
    except KeyError:
        return False


def _update_cancellable(transaction: DbTransaction) -> DbTransaction:
    """Update cancellable status"""
    if transaction.cancellable and transaction.cancellable_expire:
        if transaction.cancellable_expire < now():
            transaction.cancellable = False
            transaction.cancellable_expire = None
            transaction.save()
    return transaction