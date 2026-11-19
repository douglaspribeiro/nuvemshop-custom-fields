import { reportScriptExecution } from "../shared/script-telemetry";
import { buyerUploadControl } from "./buyer-upload-dom";
(window as unknown as { ncfBuyerUploadControl: typeof buyerUploadControl }).ncfBuyerUploadControl=buyerUploadControl;
window.dispatchEvent(new Event("ncf-buyer-uploads-ready"));

reportScriptExecution("buyer-upload-dom");
