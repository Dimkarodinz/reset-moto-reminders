#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "nokogiri"
require "set"

root = File.expand_path("..", __dir__)
docs = File.join(root, "docs")
locales = %w[en de es fr uk].freeze
pages = %w[index.html install-android.html install-ios.html privacy.html].freeze
expected_hreflang = Set.new(locales + ["x-default"])

pages.each do |page|
  locales.each do |locale|
    path = locale == "en" ? File.join(docs, page) : File.join(docs, locale, page)
    document = Nokogiri::HTML(File.read(path))
    raise "Wrong language in #{path}" unless document.at_css("html")["lang"] == locale
    raise "Missing title in #{path}" if document.at_css("title")&.text.to_s.strip.empty?
    raise "Missing canonical URL in #{path}" unless document.css('link[rel="canonical"]').length == 1
    alternates = Set.new(document.css('link[rel="alternate"][hreflang]').map { |node| node["hreflang"] })
    raise "Incomplete hreflang links in #{path}" unless alternates == expected_hreflang
    raise "Incomplete language switcher in #{path}" unless document.css(".language-links a").length == locales.length
    document.css('script[type="application/ld+json"]').each { |script| JSON.parse(script.text) }
  end
end

sitemap = Nokogiri::XML(File.read(File.join(docs, "sitemap.xml")))
sitemap.remove_namespaces!
raise "Sitemap must contain 20 localized URLs" unless sitemap.css("url > loc").length == 20

puts "Validated 20 localized pages and sitemap entries."
