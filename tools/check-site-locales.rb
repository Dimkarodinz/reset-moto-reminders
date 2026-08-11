#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "nokogiri"
require "set"
require "yaml"

root = File.expand_path("..", __dir__)
docs = File.join(root, "docs")
locales = %w[en de es fr uk].freeze
pages = %w[index.html install-android.html install-ios.html privacy.html].freeze
expected_hreflang = Set.new(locales + ["x-default"])
feedback_form_url = "https://github.com/Dimkarodinz/reset-moto-reminders/issues/new?template=motorcycle-test.yml"

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

locales.each do |locale|
  path = locale == "en" ? File.join(docs, "index.html") : File.join(docs, locale, "index.html")
  document = Nokogiri::HTML(File.read(path))
  raise "Missing motorcycle-test form link in #{path}" unless document.at_css("a[href='#{feedback_form_url}']")
end

form_path = File.join(root, ".github", "ISSUE_TEMPLATE", "motorcycle-test.yml")
form = YAML.safe_load(File.read(form_path))
field_ids = Set.new(form.fetch("body").filter_map { |field| field["id"] })
required_fields = Set.new(%w[motorcycle exact_model platform adapter app_version connection service_reset dtc_read dtc_clear privacy])
raise "Motorcycle-test form is missing required fields" unless required_fields.subset?(field_ids)

sitemap = Nokogiri::XML(File.read(File.join(docs, "sitemap.xml")))
sitemap.remove_namespaces!
raise "Sitemap must contain 20 localized URLs" unless sitemap.css("url > loc").length == 20

puts "Validated 20 localized pages and sitemap entries."
