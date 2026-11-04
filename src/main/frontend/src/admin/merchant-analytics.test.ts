import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";
import { expect, it } from "vitest";

const fragment=readFileSync(new URL("../../../resources/templates/fragments/analytics.html",import.meta.url),"utf8");
const inline=fragment.match(/<script th:inline="javascript">([\s\S]*?)<\/script>/)![1];
function render(context: Record<string,string>) {
    const dom=new JSDOM("<html></html>",{url:"https://app.test/admin/help?token=secret&storeId=999",runScripts:"outside-only"});
    const code=inline.replace(/\/\*\[\[\$\{ga4MeasurementId\}\]\]\*\/ ''/g,JSON.stringify("G-TEST123"))
        .replace(/\/\*\[\[\$\{ga4StoreContext\}\]\]\*\/ \{\}/,JSON.stringify(context));
    dom.window.eval(code);
    const calls=(dom.window as unknown as {dataLayer: IArguments[]}).dataLayer.map(entry=>Array.from(entry));
    dom.window.close(); return calls;
}
it("sets the store before the initial page view and attaches it to the admin navigation event",()=>{
    const calls=render({store_id:"959276",admin_screen:"help"});
    expect(calls[1]).toEqual(["set","user_properties",{store_id:"959276"}]);
    expect(calls[2]).toEqual(["set",{store_id:"959276",app_area:"merchant_admin",admin_screen:"help"}]);
    const config=calls.find(entry=>entry[0]==="config")![2] as {page_location:string;page_referrer:string};
    expect(config.page_location).toBe("https://app.test/admin/help"); expect(config.page_referrer).toBe("");
    expect(calls.filter(entry=>entry[0]==="event")).toEqual([["event","admin_page_view",{
        store_id:"959276",app_area:"merchant_admin",admin_screen:"help",
    }]]);
    expect(JSON.stringify(calls)).not.toContain("secret");
});
it("clears stale store properties and emits no admin event for anonymous or non-admin pages",()=>{
    const calls=render({});
    expect(calls[1]).toEqual(["set","user_properties",{store_id:null}]);
    expect(calls.some(entry=>entry[0]==="event")).toBe(false);
});
