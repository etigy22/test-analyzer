import { deflateRawSync } from 'node:zlib';
export function zip(files: Record<string,string>, compressed=false) {
  const local:Buffer[]=[],central:Buffer[]=[];let offset=0;
  for(const [name,text] of Object.entries(files)){
    const path=Buffer.from(name),body=Buffer.from(text),data=compressed?deflateRawSync(body):body;
    let crc=0xffffffff;for(const byte of body){crc^=byte;for(let i=0;i<8;i++)crc=(crc>>>1)^((crc&1)?0xedb88320:0);}crc=(crc^0xffffffff)>>>0;
    const header=Buffer.alloc(30);header.writeUInt32LE(0x04034b50);header.writeUInt16LE(20,4);header.writeUInt16LE(compressed?8:0,8);header.writeUInt32LE(crc,14);header.writeUInt32LE(data.length,18);header.writeUInt32LE(body.length,22);header.writeUInt16LE(path.length,26);
    const dir=Buffer.alloc(46);dir.writeUInt32LE(0x02014b50);dir.writeUInt16LE(20,4);dir.writeUInt16LE(20,6);dir.writeUInt16LE(compressed?8:0,10);dir.writeUInt32LE(crc,16);dir.writeUInt32LE(data.length,20);dir.writeUInt32LE(body.length,24);dir.writeUInt16LE(path.length,28);dir.writeUInt32LE(offset,42);
    local.push(header,path,data);central.push(dir,path);offset+=header.length+path.length+data.length;
  }
  const directory=Buffer.concat(central),end=Buffer.alloc(22);end.writeUInt32LE(0x06054b50);end.writeUInt16LE(Object.keys(files).length,8);end.writeUInt16LE(Object.keys(files).length,10);end.writeUInt32LE(directory.length,12);end.writeUInt32LE(offset,16);
  return Buffer.concat([...local,directory,end]);
}
export const samples:Record<string,string>={
  'fixture.java':'Runtime.getRuntime().exec(input);\ncatch (Exception e) {}',
  'fixture.py':'eval(input)\nexcept:\n    pass\npassword = "sensitive-secret-example-123456"',
  'fixture.rs':'Command::new(input);\nvalue.unwrap();',
  'fixture.swift':'let p = Process()\nlet x = try! read()',
  'fixture.ts':'eval(input);\nif (a == b) {}\nconst html = "<img src=x onerror=alert(1)>";',
  'fixture.cpp':'strcpy(target,input);\nusing namespace std;',
  'fixture.cs':'Process.Start(input);\ncatch (Exception e) {}',
  'fixture.go':'exec.Command(input)\n_, _ := read()',
  'fixture.kt':'Runtime.getRuntime().exec(input)\nval x = value!!',
  'fixture.php':'eval($input);\nif ($a == $b) {}',
  'package-lock.json':'{"packages":{"node_modules/lodash":{"version":"4.17.20"}}}',
  'package.json':'{"scripts":{"postinstall":"touch /tmp/repoguard-executed-marker"}}',
  'eslint.config.js':'require("fs").writeFileSync("/tmp/repoguard-executed-marker", "executed")',
};
