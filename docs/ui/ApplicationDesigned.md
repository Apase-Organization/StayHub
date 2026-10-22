# Screen references and TARGET contracts

**Các ảnh bên dưới là LEGACY minh họa**, không phải screenshot target đã triển khai hay evidence runtime. Giữ để trace visual direction; [TARGET DesignSystem](UXUI_DesignSystem.md) và [canonical](../business/PropertyInventoryBusinessRules.md) có ưu tiên hơn hình.

| Screen TARGET | Nội dung bắt buộc (chưa implemented) |
|---|---|
| Home/Search | Dates + rooms/adults/child ages/allocation; một card/property, total-stay basis, no-dates/loading/error/soldout riêng |
| Detail | Gallery lớn, type cards/accordion ảnh/capacity/beds/amenities riêng; plans/quantity; sticky selection desktop, bottom sheet mobile |
| Checkout | Allocation từng phòng, expiry/consent version, all-item fee/tax totals; stale409 không partial booking/charge tự động |
| Payment/Trips | Header/items/nightly/policy snapshots; booking/payment/refund riêng; pending return không confirmed; cancel toàn booking theo policy |
| Host | Một facility Types/Plans/Calendar/Bookings/Stats, shared pool, absolute/version calendar, OOO khác stop-sale, commitments warning |
| Admin | Distinct booking counts, roomnights, ledger gross/refund/net/payable; audit/lock support future paid stays |

**S0-109-v1 FROZEN_DESIGN** [typed API](../business/contracts/s0-api-v1.json)/[fixtures](../business/contracts/s0-fixtures-v1.json).3rooms/2nights total6.800.000, Family secondnight soldout rolls back all; not rendered evidence. Terms accessible/mobile not tooltip-only. WholeVND/policyVersion/fixtureVersion/quoteHash consent, quoteexpiry distinct original900s/max3hold; age/time/review090 frozen, no zeroamount/selfowner/stacking088.095clearance blocked.

## TARGET payment v2 screen acceptance — chưa có screenshots triển khai

[Payment](../business/PaymentBusinessRules.md)/[DesignSystem](UXUI_DesignSystem.md): bothmodes core, servercards/totalStay/dueOnline/balanceDueHost/refund/consent. Fixture6.800.000: fullO6.800.000/B0, hybridO1.360.000/B5.440.000; Host/Admin grossCommission1.050.000/fundedDiscountExpense200.000/netRetention850.000/held510.000hybrid or5.950.000full/Host5.950.000. No Guestfee/internalsettlement leak; approved20%/15% productconfig, not moneyactivation.

Payment/Trips phân biệt online provider-verified với direct Host-attested, CONFIRMED không đồng nghĩa fully paid. Host screen nhập partial receipt/reversal/evidence và check-in từng booked room có acknowledgment/contested case. Admin screen refund platform pool vs Host direct responsibility, audit/proof/maker-checker, payout eligibility actual check-in+24h subject dispute/reconcile/liquidity/legal, post-payout recovery. Không payout theo ngày lịch/Host tap, không StayHub QR pass-through onsite, không monthly commission. TSK-113/114 phải có desktop/mobile/keyboard evidence cả hai modes, không dùng ảnh legacy bên dưới làm bằng chứng.

[Stitch Design Demo](https://stitch.withgoogle.com/projects/324901507144460965)

#### Logo
<img width="1024" height="1024" alt="logo" src="https://github.com/user-attachments/assets/dad8185a-1590-4f73-b1da-01d90ce87989" />

#### Home Page
<img width="1600" height="1549" alt="home_page" src="https://github.com/user-attachments/assets/e9b00a33-763d-4748-a0fa-01aeb3fa9420" />

#### Search Result
<img width="1510" height="1600" alt="search_result" src="https://github.com/user-attachments/assets/532d0954-2d24-483a-9f8f-cc0aa20b779f" />

#### Detail Property
<img width="1280" height="1792" alt="detail_property" src="https://github.com/user-attachments/assets/5140af95-019d-4617-bdf8-f7111bdc8c6b" />

#### Booking Payment
<img width="1280" height="925" alt="booking_payment" src="https://github.com/user-attachments/assets/658fac96-6b65-46ac-8f15-2fe9f2bc7270" />

#### My Booking
<img width="1280" height="670" alt="my_booking" src="https://github.com/user-attachments/assets/8ea8d0f8-8f33-4510-bb06-0647d742c751" />

#### Admin Portal
<img width="1600" height="1280" alt="admin_portal" src="https://github.com/user-attachments/assets/807800e2-ddfd-4be0-b9bd-9ce5703b78a2" />

#### Host Dashboard
<img width="1280" height="795" alt="host_dashboard" src="https://github.com/user-attachments/assets/3940f447-3ae0-444d-9679-8d4fef7b7589" />
