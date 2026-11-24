import React, {useRef, useState} from 'react';
import {ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View} from 'react-native';
import {SafeAreaProvider, SafeAreaView} from 'react-native-safe-area-context';
import {CameraView, useCameraPermissions} from 'expo-camera';
import TcpSocket from 'react-native-tcp-socket';
const {queryGertec, validEAN} = require('./src/gertec');
const types = ['ean13','ean8','upc_a','itf14'];
function Btn({title, onPress, disabled, secondary}) { return <Pressable accessibilityRole="button" disabled={disabled} onPress={onPress} style={[s.button,secondary&&s.secondary,disabled&&{opacity:.5}]}><Text style={[s.buttonText,secondary&&{color:'#174b3b'}]}>{title}</Text></Pressable>; }
function Reader() {
  const [host,setHost]=useState(''); const [port,setPort]=useState('6500');
  const [ean,setEAN]=useState(''); const [scanning,setScanning]=useState(false); const [torch,setTorch]=useState(false);
  const [permission,requestPermission]=useCameraPermissions(); const [busy,setBusy]=useState(false); const [result,setResult]=useState(null); const [error,setError]=useState(''); const [settings,setSettings]=useState(true);
  const locked=useRef(true); const pending=useRef(false);
  async function lookup(value) {
    if(pending.current) return;
    locked.current=true; setScanning(false); setTorch(false); setResult(null); setError(''); setEAN(value);
    if(!validEAN(value)) {setError('Informe um código EAN/UPC/GTIN válido.'); return;}
    if(!host.trim() || !/^\d+$/.test(port)) {setError('Configure o IP e a porta do servidor.'); setSettings(true); return;}
    pending.current=true; setBusy(true);
    try {setResult(await queryGertec(options=>TcpSocket.createConnection(options),{host:host.trim(),port:Number(port),ean:value}));}
    catch(e){setError(e.message);} finally {pending.current=false;setBusy(false);}
  }
  async function openScanner(){
    if(busy)return;
    if(!host.trim()){setSettings(true);setError('Configure o servidor primeiro.');return;}
    const granted=permission?.granted || (await requestPermission()).granted;
    if(!granted){setError('A câmera precisa de permissão. Use a digitação manual.');return;}
    locked.current=false;setError('');setScanning(true);
  }
  return <SafeAreaView style={s.safe}><ScrollView contentContainerStyle={s.page} keyboardShouldPersistTaps="handled">
    <Text style={s.brand}>OFERTAMÁTICA / MOBILE</Text><Text style={s.heading}>Consulta de preços</Text><Text style={s.muted}>Terminal móvel • Somente leitura</Text>
    <Btn title={settings?'Ocultar servidor':'Configurar servidor'} secondary disabled={busy||scanning} onPress={()=>setSettings(!settings)}/>
    {settings&&<View style={s.card}><Text style={s.label}>IP do servidor Gertec</Text><TextInput style={s.input} value={host} onChangeText={setHost} placeholder="IP autorizado pela TI" autoCapitalize="none" autoCorrect={false} editable={!busy&&!scanning}/><Text style={s.label}>Porta TCP</Text><TextInput style={s.input} keyboardType="number-pad" value={port} onChangeText={setPort} editable={!busy&&!scanning}/><Text style={s.muted}>Conecte-se à rede autorizada da loja. A configuração não é persistida nesta versão.</Text></View>}
    {scanning?<View style={s.card}><View style={s.camera}><CameraView style={StyleSheet.absoluteFill} facing="back" enableTorch={torch} barcodeScannerSettings={{barcodeTypes:types}} onBarcodeScanned={({data})=>{if(locked.current||!validEAN(data))return;locked.current=true;lookup(data);}}/><View pointerEvents="none" style={s.target}/></View><Text style={s.muted}>Aponte para o código de barras.</Text><Btn title={torch?'Desligar lanterna':'Ligar lanterna'} secondary onPress={()=>setTorch(!torch)}/><Btn title="Cancelar" secondary onPress={()=>{locked.current=true;setScanning(false);setTorch(false);}}/></View>:<Btn title="Escanear código de barras" onPress={openScanner} disabled={busy}/>}
    <View style={s.card}><Text style={s.label}>Código de barras</Text><TextInput style={s.input} value={ean} onChangeText={setEAN} keyboardType="number-pad" maxLength={14} editable={!busy&&!scanning} placeholder="Digite o EAN"/><Btn title="Consultar preço" disabled={busy||scanning||!validEAN(ean)} onPress={()=>lookup(ean)}/></View>
    {busy&&<View style={s.card}><ActivityIndicator color="#177052"/><Text style={s.muted}>Consultando servidor…</Text></View>}
    {error!==''&&<View style={s.card}><Text accessibilityRole="alert" style={s.error}>{error}</Text></View>}
    {result&&<View style={s.card}><Text style={s.label}>{result.status==='found'?'PREÇO CONSULTADO':result.status==='not_found'?'PRODUTO NÃO ENCONTRADO':'PRODUTO SEM PREÇO'}</Text>{result.name&&<Text style={s.product}>{result.name}</Text>}{result.price&&<Text style={s.price}>{result.price}</Text>}<Text style={s.muted}>EAN: {ean}</Text><Text style={s.muted}>Resposta recebida agora • sem cache</Text><Btn title="Consultar outro produto" onPress={openScanner}/></View>}
  </ScrollView></SafeAreaView>;
}
export default function App(){return <SafeAreaProvider><Reader/></SafeAreaProvider>}
const s=StyleSheet.create({safe:{flex:1,backgroundColor:'#f2f6f4'},page:{padding:20,gap:14},brand:{fontSize:12,letterSpacing:2,fontWeight:'800',color:'#177052'},heading:{fontSize:32,fontWeight:'800',color:'#112e24'},muted:{fontSize:14,color:'#52645d',lineHeight:20},label:{fontSize:13,fontWeight:'700',color:'#455b51'},card:{backgroundColor:'white',borderRadius:16,padding:16,gap:10},input:{borderWidth:1,borderColor:'#ccd8d1',borderRadius:10,padding:12,fontSize:18,color:'#112e24'},button:{backgroundColor:'#17694f',borderRadius:12,padding:15,alignItems:'center'},secondary:{backgroundColor:'#e2eee8'},buttonText:{color:'white',fontSize:16,fontWeight:'700'},camera:{height:230,borderRadius:12,overflow:'hidden',justifyContent:'center',alignItems:'center',backgroundColor:'#111'},target:{width:'85%',height:100,borderWidth:2,borderColor:'#fff',borderRadius:12},product:{fontWeight:'700',fontSize:23,color:'#112e24'},price:{fontWeight:'900',fontSize:48,color:'#17694f'},error:{color:'#a42626',fontWeight:'600'}});
