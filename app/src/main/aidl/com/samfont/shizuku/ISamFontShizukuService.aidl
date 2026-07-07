package com.samfont.shizuku;

interface ISamFontShizukuService {
    String whoami();
    String id();
    String listFontDirs();
    String checkWritable();
    String readFontConfigCandidates();
}
