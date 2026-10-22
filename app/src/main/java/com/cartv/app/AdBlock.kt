package com.cartv.app

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlock {
    private val hosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
        "googletagmanager.com", "google-analytics.com", "adservice.google.com", "ads.youtube.com",
        "adnxs.com", "adsrvr.org", "amazon-adsystem.com", "criteo.com", "criteo.net", "taboola.com",
        "outbrain.com", "pubmatic.com", "rubiconproject.com", "openx.net", "scorecardresearch.com",
        "moatads.com", "zedo.com", "popads.net", "propellerads.com", "adcolony.com", "applovin.com",
        "quantserve.com", "casalemedia.com", "smartadserver.com", "yieldmo.com", "33across.com",
        "media.net", "sharethrough.com", "teads.tv", "bidswitch.net", "3lift.com", "indexww.com",
        "adform.net", "serving-sys.com", "advertising.com", "exoclick.com", "popcash.net",
    )

    private val youtubePaths = listOf("/pagead/", "/api/stats/ads", "/ptracking", "/get_midroll_info")

    fun blocks(url: Uri): Boolean {
        val host = url.host ?: return false
        var h = host
        while (true) {
            if (h in hosts) return true
            val i = h.indexOf('.')
            if (i < 0) break
            h = h.substring(i + 1)
        }
        if (host.endsWith("youtube.com")) {
            val path = url.path ?: ""
            if (youtubePaths.any { path.startsWith(it) }) return true
        }
        return false
    }

    fun empty() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    /** Runs before YouTube's own code: strips ads from player data, skips any that slip through. */
    val SCRIPT = """
(function(){
  if (!/(^|\.)youtube\.com$/.test(location.hostname)) return;
  var KEYS = ['adPlacements','playerAds','adSlots','adBreakHeartbeatParams'];
  function prune(o){
    try {
      if (o && typeof o === 'object') {
        KEYS.forEach(function(k){ if (k in o) delete o[k]; });
        if (o.playerResponse) prune(o.playerResponse);
      }
    } catch(e) {}
    return o;
  }
  var jp = JSON.parse;
  JSON.parse = function(){ return prune(jp.apply(this, arguments)); };
  var rj = Response.prototype.json;
  Response.prototype.json = function(){ return rj.apply(this, arguments).then(prune); };

  var css = 'ytm-promoted-sparkles-web-renderer,ytm-promoted-video-renderer,ytm-companion-ad-renderer,' +
    'ad-slot-renderer,ytm-ad-slot-renderer,ytm-display-ad-renderer,ytm-statement-banner-renderer,' +
    'ytm-mealbar-promo-renderer,.ytp-ad-overlay-container,.ytp-ad-module,#player-ads{display:none!important}';
  (function addCss(){
    if (!document.head) { setTimeout(addCss, 50); return; }
    var s = document.createElement('style'); s.textContent = css; document.head.appendChild(s);
  })();

  var adOn = false;
  setInterval(function(){
    var p = document.querySelector('.html5-video-player');
    var v = p && p.querySelector('video');
    var ad = !!(p && (p.classList.contains('ad-showing') || p.classList.contains('ad-interrupting')));
    if (v && ad) {
      adOn = true; v.muted = true;
      if (isFinite(v.duration) && v.duration > 0) v.currentTime = v.duration;
    } else if (v && adOn) {
      adOn = false; v.muted = false;
    }
    document.querySelectorAll('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button')
      .forEach(function(b){ b.click(); });
  }, 300);
})();
"""
}
