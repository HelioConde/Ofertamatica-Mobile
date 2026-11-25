const {test}=require('node:test');const assert=require('node:assert/strict');
const {defaults,validServer,readSettings}=require('../src/settings');
test('IPv4 and port validation',()=>{assert.equal(validServer({host:'192.0.2.250',port:'6500'}),true);assert.equal(validServer({host:'192.268.2.250',port:'6500'}),false);assert.equal(validServer({host:'192.0.2.250',port:'65536'}),false);assert.equal(validServer({host:'192.0.2.250',port:'0'}),false);});
test('valid saved settings take priority',()=>assert.deepEqual(readSettings('{"host":"192.0.2.250","port":"6501"}'),{host:'192.0.2.250',port:'6501'}));
test('invalid saved settings restore build defaults',()=>{assert.deepEqual(readSettings('invalid'),defaults);assert.deepEqual(readSettings('{"host":"bad","port":"0"}'),defaults);});
