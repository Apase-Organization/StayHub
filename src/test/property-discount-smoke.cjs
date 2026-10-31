// Run directly with node; executes the production inline script with a minimal DOM.
const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const html = readFileSync('src/main/resources/templates/property/property-detail.html', 'utf8');
const nodes = new Map();
function node(id) {
 if (!nodes.has(id)) nodes.set(id, {value: '', children: [], dataset: {}, classList: {add(){},remove(){}},
  addEventListener(event, fn){this[event]=fn;}, replaceChildren(){this.children=[];}, append(...v){this.children.push(...v);}, querySelectorAll(){return [];}});
 return nodes.get(id);
}
let resolveFetch, sent;
const context = vm.createContext({document: {getElementById:node, querySelector:()=>({content:''}), createElement:()=>node(Symbol())},
 crypto:{randomUUID:()=> 'key'}, fetch:async (url, options)=>{sent=JSON.parse(options.body); return new Promise(resolve=>{resolveFetch=resolve;});}, console});
vm.runInContext(html.match(/<script th:inline="javascript">([\s\S]*?)<\/script>/)[1].replace('/*[[${canBookProperty}]]*/ false','true'),context);
vm.runInContext("selectedOffers.push({offer:{roomTypeId:1,ratePlanId:2},roomOccupancies:[{index:0,adults:1,childrenAges:[]}]})",context);
node('offer-check-in').value='2026-12-01'; node('offer-check-out').value='2026-12-02';
node('offer-payment-mode').value='HYBRID_DEPOSIT'; node('discount-code').value='StayHub25';
const quote = {subtotal:200000,discount:25000,totalStay:175000,dueOnline:35000,balanceDueHost:140000,
 quoteExpiresAt:'2026-10-31T01:12:47.504878Z'};
assert.equal(vm.runInContext("formatQuoteExpiry('2026-10-31T01:12:47.504878Z')",context),
 '08:12:47 ngày 31/10/2026');
assert.equal(vm.runInContext("formatQuoteExpiry('2026-10-31T20:05:00Z')",context),
 '03:05:00 ngày 01/11/2026');
for (const value of ['null', "''", "'invalid'"]) {
 assert.equal(vm.runInContext(`formatQuoteExpiry(${value})`,context),'Chưa có thông tin thời gian hết hạn');
}
(async()=>{
 let pending=node('request-quote').click();
 assert.equal(sent.discountCode,'StayHub25');
 resolveFetch({ok:true,json:async()=>({success:true,data:quote})}); await pending;
  assert.match(node('quote-summary').textContent,/175,000/);
  assert.match(node('quote-summary').textContent,/Báo giá có hiệu lực đến 08:12:47 ngày 31\/10\/2026$/);
 assert.match(node('discount-status').textContent,/StayHub25/);
 node('quote-consent').checked=true;
 pending=node('request-quote').click();
 node('discount-code').value=''; node('discount-code').input();
 resolveFetch({ok:true,json:async()=>({success:true,data:quote})}); await pending;
 assert.equal(vm.runInContext('acceptedQuote',context),null);
 assert.equal(node('quote-consent').checked,false);
 assert.equal(node('confirm-quote').disabled,true);
 console.log('PASS: discount request/amounts and late-response invalidation');
})().catch(error=>{console.error(error);process.exitCode=1;});
