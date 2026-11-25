import React, {useEffect, useRef, useState} from 'react';
import {ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View} from 'react-native';
import {SafeAreaProvider, SafeAreaView} from 'react-native-safe-area-context';
import {CameraView, useCameraPermissions} from 'expo-camera';
import TcpSocket from 'react-native-tcp-socket';
import AsyncStorage from '@react-native-async-storage/async-storage';
const {defaults, validServer, readSettings} = require('./src/settings');
const {queryGertec, validEAN} = require('./src/gertec');
const types = ['ean13','ean8','upc_a','itf14'];
function Btn({title, onPress, disabled, secondary}) { return <Pressable accessibilityRole="button" disabled={disabled} onPress={onPress} style={[s.button,secondary&&s.secondary,disabled&&{opacity:.5}]}><Text style={[s.buttonText,secondary&&{color:'#174b3b'}]}>{title}</Text></Pressable>; }
function Reader() {
  const [host,setHost]=useState(defaults.host); const [port,setPort]=useState(defaults.port);
  const [draftHost,setDraftHost]=useState(defaults.host); const [draftPort,setDraftPort]=useState(defaults.port); const [settingsError,setSettingsError]=useState(''); const [ready,setReady]=useState(false); const [saving,setSaving]=useState(false);
  const [ean,setEAN]=useState(''); const [scanning,setScanning]=useState(false); const [torch,setTorch]=useState(false);
  const [permission,requestPermission]=useCameraPermissions(); const [busy,setBusy]=useState(false); const [result,setResult]=useState(null); const [error,setError]=useState(''); const [settings,setSettings]=useState(false);
  const locked=useRef(true); const pending=useRef(false);
  useEffect(()=>{
    let active=true;
    AsyncStorage.getItem('primor.server.v1').then(raw=>{if(active){const config=readSettings(raw);setHost(config.host);setPort(config.port);}}).catch(()=>{}).finally(()=>{if(active)setReady(true);});
    return ()=>{active=false;};
  },[]);
  function showSettings() {setDraftHost(host);setDraftPort(port);setSettingsError('');setSettings(true);}
  async function saveSettings(){
    const config={host:draftHost.trim(),port:draftPort.trim()};
    if(!validServer(config)){setSettingsError('Informe um IPv4 válido e uma porta de 1 a 65535.');return;}
    setSaving(true);
    try{await AsyncStorage.setItem('primor.server.v1',JSON.stringify(config));setHost(config.host);setPort(config.port);setSettings(false);setError('');setResult(null);}
    catch{setSettingsError('Não foi possível salvar a configuração.');}
    finally{setSaving(false);}
  }
  async function lookup(value) {
    if(pending.current||!ready||settings) return;
    locked.current=true; setScanning(false); setTorch(false); setResult(null); setError(''); setEAN(value);
    if(!validEAN(value)) {setError('Informe um código EAN/UPC/GTIN válido.'); return;}
    if(!validServer({host,port})) {setError('Configure o IP e a porta do servidor.'); showSettings(); return;}
    pending.current=true; setBusy(true);
    try {setResult(await queryGertec(options=>TcpSocket.createConnection(options),{host:host.trim(),port:Number(port),ean:value}));}
    catch(e){setError(e.message);} finally {pending.current=false;setBusy(false);}
  }
  async function openScanner(){
    if(busy||!ready||settings)return;
    if(!validServer({host,port})){showSettings();setError('Configure o servidor primeiro.');return;}
    const granted=permission?.granted || (await requestPermission()).granted;
    if(!granted){setError('A câmera precisa de permissão. Use a digitação manual.');return;}
    locked.current=false;setError('');setScanning(true);
  }
  return <SafeAreaView style={s.safe}><ScrollView contentContainerStyle={s.page} keyboardShouldPersistTaps="handled">
    <View style={s.header}><View style={s.headerText}><Text style={s.brand}>PRIMOR SUPERMERCADO</Text><Text style={s.heading}>Consultor de Preço</Text></View><Pressable accessibilityRole="button" accessibilityLabel={settings?'Fechar configurações':'Abrir configurações do servidor'} accessibilityState={{expanded:settings,disabled:busy||scanning||!ready||saving}} disabled={busy||scanning||!ready||saving} onPress={()=>settings?setSettings(false):showSettings()} style={s.menu}><Text style={s.menuIcon}>{settings?'×':'☰'}</Text></Pressable></View>
    <Text style={s.muted}>Terminal móvel • Somente leitura</Text>
    {settings&&<View style={s.card}><Text style={s.product}>Configurações do servidor</Text><Text style={s.label}>IP do servidor Gertec</Text><TextInput style={s.input} value={draftHost} onChangeText={setDraftHost} placeholder="IP autorizado pela TI" autoCapitalize="none" autoCorrect={false} editable={!saving}/><Text style={s.label}>Porta TCP</Text><TextInput style={s.input} keyboardType="number-pad" value={draftPort} onChangeText={setDraftPort} editable={!saving}/>{settingsError!==''&&<Text accessibilityRole="alert" style={s.error}>{settingsError}</Text>}<Btn title="Salvar configurações" onPress={saveSettings} disabled={saving}/><Btn title="Restaurar padrão" secondary disabled={saving} onPress={()=>{setDraftHost(defaults.host);setDraftPort(defaults.port);setSettingsError('');}}/><Btn title="Cancelar" secondary disabled={saving} onPress={()=>setSettings(false)}/><Text style={s.muted}>Configuração salva apenas neste aparelho. Use a rede autorizada da loja.</Text></View>}
    {scanning?<View style={s.card}><View style={s.camera}><CameraView style={StyleSheet.absoluteFill} facing="back" enableTorch={torch} barcodeScannerSettings={{barcodeTypes:types}} onBarcodeScanned={({data})=>{if(locked.current||!validEAN(data))return;locked.current=true;lookup(data);}}/><View pointerEvents="none" style={s.target}/></View><Text style={s.muted}>Aponte para o código de barras.</Text><Btn title={torch?'Desligar lanterna':'Ligar lanterna'} secondary onPress={()=>setTorch(!torch)}/><Btn title="Cancelar" secondary onPress={()=>{locked.current=true;setScanning(false);setTorch(false);}}/></View>:<Btn title="Escanear código de barras" onPress={openScanner} disabled={busy||!ready||settings}/>}
    <View style={s.card}><Text style={s.label}>Código de barras</Text><TextInput style={s.input} value={ean} onChangeText={setEAN} keyboardType="number-pad" maxLength={14} editable={!busy&&!scanning&&!settings} placeholder="Digite o EAN"/><Btn title="Consultar preço" disabled={busy||scanning||!ready||settings||!validEAN(ean)} onPress={()=>lookup(ean)}/></View>
    {busy&&<View style={s.card}><ActivityIndicator color="#177052"/><Text style={s.muted}>Consultando servidor…</Text></View>}
    {error!==''&&<View style={s.card}><Text accessibilityRole="alert" style={s.error}>{error}</Text></View>}
    {result&&<View style={s.card}><Text style={s.label}>{result.status==='found'?'PREÇO CONSULTADO':result.status==='not_found'?'PRODUTO NÃO ENCONTRADO':'PRODUTO SEM PREÇO'}</Text>{result.name&&<Text style={s.product}>{result.name}</Text>}{result.price&&<Text style={s.price}>{result.price}</Text>}<Text style={s.muted}>EAN: {ean}</Text><Text style={s.muted}>Resposta recebida agora • sem cache</Text><Btn title="Consultar outro produto" onPress={openScanner} disabled={settings||!ready}/></View>}
  </ScrollView></SafeAreaView>;
}
export default function App(){return <SafeAreaProvider><Reader/></SafeAreaProvider>}
const s=StyleSheet.create({safe:{flex:1,backgroundColor:'#f2f6f4'},page:{padding:20,gap:14},header:{flexDirection:'row',alignItems:'center',gap:12},headerText:{flex:1},menu:{width:48,height:48,borderRadius:12,backgroundColor:'#e2eee8',alignItems:'center',justifyContent:'center'},menuIcon:{fontSize:27,color:'#174b3b',fontWeight:'700'},brand:{fontSize:12,letterSpacing:2,fontWeight:'800',color:'#177052'},heading:{fontSize:28,fontWeight:'800',color:'#112e24'},muted:{fontSize:14,color:'#52645d',lineHeight:20},label:{fontSize:13,fontWeight:'700',color:'#455b51'},card:{backgroundColor:'white',borderRadius:16,padding:16,gap:10},input:{borderWidth:1,borderColor:'#ccd8d1',borderRadius:10,padding:12,fontSize:18,color:'#112e24'},button:{backgroundColor:'#17694f',borderRadius:12,padding:15,alignItems:'center'},secondary:{backgroundColor:'#e2eee8'},buttonText:{color:'white',fontSize:16,fontWeight:'700'},camera:{height:230,borderRadius:12,overflow:'hidden',justifyContent:'center',alignItems:'center',backgroundColor:'#111'},target:{width:'85%',height:100,borderWidth:2,borderColor:'#fff',borderRadius:12},product:{fontWeight:'700',fontSize:23,color:'#112e24'},price:{fontWeight:'900',fontSize:48,color:'#17694f'},error:{color:'#a42626',fontWeight:'600'}});
