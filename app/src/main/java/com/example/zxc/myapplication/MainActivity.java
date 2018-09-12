package com.example.zxc.myapplication;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.NfcA;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private NfcAdapter nfc;
    private TextView promptText;

    public static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || "".equals(hexString)) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        promptText = (TextView) findViewById(R.id.prompt);
        nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc == null) {
            promptText.setText("不支持NFC");
        } else if (!nfc.isEnabled()) {
            promptText.setText("NFC未开");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(getIntent().getAction())) {
            Log.w("findnfc", "1");
            promptText.setText("detect NFC");
            new ParseTask().execute(getIntent());
        }
    }

    public String readFromTag(Intent intent) {
        Log.d("tag1", "ttt");
        StringBuilder metaInfo = new StringBuilder("NFC\n");

        // ? 为什么使用二位数组
        byte[][] a;
        // 一些有可能的密码
        String[] sKey = {"FFFFFFFFFFFF", "A0A1A2A3A4A5", "D3F7D3F7D3F7", "000000000000", "A0B0C0D0E0F0",
                "A1B1C1D1E1F1", "B0B1B2B3B4B5", "4D3A99C351DD", "1A982C7E459A", "AABBCCDDEEFF", "B5FF67CBA951",
                "714C5C886E97", "587EE5F9350F", "A0478CC39091", "533CB6C723F6", "24020000DBFD", "000012ED12ED",
                "8FD0A4F256E9", "EE9BD361B01B", "FFzzzzzzzzzz", "A0zzzzzzzzzz"};
        boolean[] auth = new boolean[sKey.length];
        a = new byte[sKey.length][];
        for (int t = 0; t < sKey.length; t++) {
            a[t] = hexStringToBytes(sKey[t]);
        }
        Tag nfcTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        for (String tech : nfcTag.getTechList()) {
            metaInfo.append(tech).append("\n");
        }
        NfcA n = NfcA.get(nfcTag);
        MifareUltralight mfu = MifareUltralight.get(nfcTag);
        MifareClassic mfc = MifareClassic.get(nfcTag);

        try {
            mfc.connect();
            // 获取tag类型
            int type = mfc.getType();
            // 扇区数
            int sectorCount = mfc.getSectorCount();

            String typeS = "";
            switch (type) {
                case MifareClassic.TYPE_CLASSIC:
                    typeS = "TYPE_CLASSIC";
                    break;
                case MifareClassic.TYPE_PLUS:
                    typeS = "TYPE_PLUS";
                    break;
                case MifareClassic.TYPE_PRO:
                    typeS = "TYPE_PRO";
                    break;
                case MifareClassic.TYPE_UNKNOWN:
                    typeS = "TYPE_UNKNOWN";
                    break;
                default:
            }
            metaInfo = new StringBuilder("card type" + mfc.getType() + "\n扇区数:" + sectorCount
                    + "\n存储空间块数" + mfc.getBlockCount() + "\n存储空间大小" + mfc.getSize() + "B\n");

            // 遍历扇区, 穷举密码
            for (int j = 0; j < sectorCount; j++) {
                for (int m = 0; m < a.length; m++) {
                    // verify permission with KeyA
                    auth[m] = mfc.authenticateSectorWithKeyA(j, a[m]);
                    if (!auth[m]) {
                        // verify permission with KeyB
                        auth[m] = mfc.authenticateSectorWithKeyB(j, a[m]);
                    }

                    if (!auth[m]) {
                        continue;
                    }

                    // 该扇区认证成功, 读取块信息
                    int blockNum = 0;
                    int bIndex = 0;
                    metaInfo.append("Sector ").append(j).append(" verified");
                    // 读取扇区中的块
                    blockNum = mfc.getBlockCountInSector(j);
                    // 盘块对应到物理块
                    bIndex = mfc.sectorToBlock(j);
                    for (int num = 0; num < blockNum; num++) {
                        byte[] data = mfc.readBlock(bIndex);
                        metaInfo.append("Block ").append(bIndex).append(":").append(b2hex(data)).append("\n");
                        bIndex++;
                    }
                    break;
                }
            }
                /*
                auth[auth.length-1]=
                        mfc.authenticateSectorWithKeyA(j,MifareClassic.KEY_DEFAULT)||
                        mfc.authenticateSectorWithKeyB(j,MifareClassic.KEY_DEFAULT);
                auth[auth.length-2]=
                        mfc.authenticateSectorWithKeyA(j,MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY)||
                        mfc.authenticateSectorWithKeyB(j,MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY)||
                        mfc.authenticateSectorWithKeyA(j,MifareClassic.KEY_NFC_FORUM)||
                        mfc.authenticateSectorWithKeyB(j,MifareClassic.KEY_NFC_FORUM);
                */

        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return metaInfo.toString();


        //prompttxt.setText(metaInfo);
    }

    public String b2hex(byte[] src) {
        if (src == null || src.length == 0) {
            return null;
        }
        StringBuilder sbd = new StringBuilder();
        for (int k = 0; k < src.length; k++) {
            int hnum = src[k] & 0xFF;
            String s = Integer.toHexString(hnum);
            if (s.length() < 2) {
                sbd.append('0');
            }
            sbd.append(hnum);
        }
        return sbd.toString();
    }

    class ParseTask extends AsyncTask<Intent, Integer, String> {

        @Override
        protected String doInBackground(Intent... params) {
            return readFromTag(params[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (result != null) {
                promptText.setText(result);
            } else {
                promptText.setText("failed");
            }
        }
    }
}
