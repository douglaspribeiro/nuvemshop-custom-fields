import {readFileSync} from "node:fs";
import {JSDOM} from "jsdom";
import {afterEach,expect,it,vi} from "vitest";

const source=readFileSync(new URL("../../../resources/static/assets/template-batch.js",import.meta.url),"utf8");
const html=readFileSync(new URL("../../../resources/templates/admin/template-batch.html",import.meta.url),"utf8");
let dom:JSDOM;
afterEach(()=>dom?.window.close());
function boot(modified=false,resume=false) {
    dom=new JSDOM(html,{url:"https://app.test/admin/templates/batch?key=own:1",runScripts:"outside-only"});
    const doc=dom.window.document,root=doc.getElementById("template-batch")!;
    root.dataset.key="own:1";if(resume)root.dataset.batchId="saved";
    const field={label:"<img src=x onerror=alert(1)>",fieldType:"TEXT",required:true,maxLength:20};
    let batch={id:"saved",key:"own:1",mode:"APPEND",revision:1,items:[
        {productId:1,name:"Camisa",before:[],after:[field],managed:[field],modified,state:"READY",error:null,attempts:0},
        {productId:2,name:"Caneca",before:[],after:[field],managed:[field],modified:false,state:resume?"SUCCEEDED":"READY",error:null,attempts:0}
    ]};
    const fetch=vi.fn(async(url:string,options?:{body:string})=>{
        let data:unknown;
        if(url.endsWith("/history"))data=[{id:"saved",key:"own:1",mode:"APPEND",succeeded:resume?1:0,total:2}];
        else if(url.endsWith("/products"))data=[{id:1,name:"Camisa",brand:"A",categories:{"10":"Roupas"},configured:false},{id:2,name:"Caneca",brand:"B",categories:{"20":"Presentes"},configured:true}];
        else if(url.endsWith("/execute")) {const body=JSON.parse(options!.body);batch={...batch,items:batch.items.map(i=>i.productId===body.productId?{...i,state:"SUCCEEDED"}:i)};data=batch;}
        else data=batch;
        return {ok:true,json:async()=>data};
    });
    Object.assign(dom.window,{fetch,ncfTemplateBatchUi:{"template.confirm":"Confirme",selection:"Selecionados: {0}","batch.modified.confirm":"Autorize a edição"}});
    dom.window.eval(source);return {doc,fetch};
}
function click(doc:Document,id:string){(doc.getElementById("batch-"+id) as HTMLButtonElement).click();}
it("filters products, previews safe text and submits only explicitly selected products after confirmation",async()=>{
    const {doc,fetch}=boot();await vi.waitFor(()=>expect(doc.querySelectorAll("#batch-products input")).toHaveLength(2));
    const category=doc.getElementById("batch-category") as HTMLSelectElement;category.value="10";category.dispatchEvent(new dom.window.Event("change"));
    expect(doc.querySelectorAll("#batch-products input")).toHaveLength(1);click(doc,"select-all");click(doc,"review");
    await vi.waitFor(()=>expect(doc.querySelectorAll("[data-product]")).toHaveLength(2));
    expect(doc.querySelector("#batch-review-items img")).toBeNull();expect(doc.getElementById("batch-review-items")!.textContent).toContain("<img src=x");
    const review=fetch.mock.calls.find(([url])=>url.endsWith("/review"));expect(JSON.parse(review![1]!.body).productIds).toEqual([1]);
    click(doc,"execute");expect(fetch.mock.calls.filter(([url])=>url.endsWith("/execute"))).toHaveLength(0);
    (doc.getElementById("batch-confirm") as HTMLInputElement).checked=true;
    doc.querySelector<HTMLInputElement>('[data-product="2"]')!.click();click(doc,"execute");
    await vi.waitFor(()=>expect(fetch.mock.calls.filter(([url])=>url.endsWith("/execute"))).toHaveLength(1));
    expect(JSON.parse(fetch.mock.calls.find(([url])=>url.endsWith("/execute"))![1]!.body).productId).toBe(1);
    await vi.waitFor(()=>expect((doc.getElementById("batch-execute") as HTMLButtonElement).disabled).toBe(false));
});
it("resumes saved progress and requires explicit approval for locally modified products",async()=>{
    const {doc,fetch}=boot(true,true);await vi.waitFor(()=>expect(doc.querySelectorAll("[data-product]")).toHaveLength(2));
    const modified=doc.querySelector<HTMLInputElement>('[data-product="1"]')!,done=doc.querySelector<HTMLInputElement>('[data-product="2"]')!;
    expect(modified.checked).toBe(false);expect(done.disabled).toBe(true);
    modified.click();(doc.getElementById("batch-confirm") as HTMLInputElement).checked=true;click(doc,"execute");
    expect(fetch.mock.calls.filter(([url])=>url.endsWith("/execute"))).toHaveLength(0);
    doc.querySelector<HTMLInputElement>('[data-modified="1"]')!.click();click(doc,"execute");
    await vi.waitFor(()=>expect(fetch.mock.calls.filter(([url])=>url.endsWith("/execute"))).toHaveLength(1));
    expect(JSON.parse(fetch.mock.calls.find(([url])=>url.endsWith("/execute"))![1]!.body).allowModified).toBe(true);
    await vi.waitFor(()=>expect((doc.getElementById("batch-execute") as HTMLButtonElement).disabled).toBe(false));
});
