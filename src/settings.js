// Expo substitutes this build-time value. Never commit a store's internal IP.
const defaults = Object.freeze({host:process.env.EXPO_PUBLIC_GERTEC_HOST || '',port:'6500'});
function validServer(config){
 if(!config || typeof config.host!=='string' || typeof config.port!=='string')return false;
 const parts=config.host.split('.');
 return parts.length===4 && parts.every(p=>/^(0|[1-9]\d{0,2})$/.test(p)&&Number(p)<=255)&&/^\d{1,5}$/.test(config.port)&&Number(config.port)>=1&&Number(config.port)<=65535;
}
function readSettings(raw){
 try{const saved=JSON.parse(raw);if(validServer(saved))return saved;}catch{}
 return {...defaults};
}
module.exports={defaults,validServer,readSettings};
