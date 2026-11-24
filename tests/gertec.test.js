const {test}=require('node:test');const assert=require('node:assert/strict');const net=require('node:net');const {parseResponse,queryGertec,validEAN,ID}=require('../src/gertec');
test('preço, controles, centavos',()=>assert.deepEqual(parseResponse('\x02#MOLHO IMPERADOR 145M|R$ 6,99\x03'),{status:'found',name:'MOLHO IMPERADOR 145M',price:'R$ 6,99',cents:699}));
test('sem preço e inexistente',()=>{assert.deepEqual(parseResponse('#X|SEM PRECO'),{status:'no_price',name:'X'});assert.deepEqual(parseResponse('#nfound'),{status:'not_found'});});
test('rejeita resposta incompleta e EAN não numérico',()=>{assert.equal(parseResponse('#X|R$ 6,'),null);assert.equal(parseResponse('#X|R$ 6,99#nfound'),null);assert.equal(validEAN('#12345678'),false);assert.equal(validEAN('0123456789012'),true);});
async function simulated(t, response, alwaysLive=false) {
 const clients=new Set();const server=net.createServer(socket=>{clients.add(socket);socket.on('close',()=>clients.delete(socket));socket.write('#o');setTimeout(()=>{if(!socket.destroyed)socket.write('k');},5);socket.on('data',d=>{const msg=d.toString();if(msg===ID){if(alwaysLive)socket.write('#alwayslive');}else if(msg==='#7898016923542'){if(response){socket.write(response.slice(0,8));setTimeout(()=>{if(!socket.destroyed)socket.write(response.slice(8));},5);}}});});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));t.after(()=>{for(const c of clients)c.destroy();return new Promise(r=>server.close(r));});
 return queryGertec(net.createConnection,{host:'127.0.0.1',port:server.address().port,ean:'7898016923542',timeoutMs:700,settleMs:30,identifyDelayMs:30});
}
test('TCP real local: handshake e resposta fragmentada',async t=>assert.equal((await simulated(t,'#MOLHO IMPERADOR 145M|R$ 6,99')).cents,699));
test('TCP real local: alwayslive e produto inexistente',async t=>assert.equal((await simulated(t,'#nfound',true)).status,'not_found'));
test('TCP real local: timeout sem resposta',async t=>await assert.rejects(simulated(t,null),/Tempo limite/));
