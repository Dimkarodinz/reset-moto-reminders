#!/usr/bin/env ruby
# Generates localized GitHub Pages from the English pages under docs/.
# English remains the source layout and the x-default locale.

require "json"
require "nokogiri"

ROOT = File.expand_path("..", __dir__)
DOCS = File.join(ROOT, "docs")
BASE_URL = "https://dimkarodinz.github.io/reset-moto-reminders"
PAGES = %w[index.html install-android.html install-ios.html privacy.html].freeze
LOCALES = {
  "en" => {label: "EN", directory: nil},
  "de" => {label: "DE", directory: "de"},
  "es" => {label: "ES", directory: "es"},
  "fr" => {label: "FR", directory: "fr"},
  "uk" => {label: "UK", directory: "uk"},
}.freeze

PRESERVED_TEXT = [
  "Reset Moto Reminders", "Android", "iPhone", "Triumph Tiger 900 GT Pro, 2021",
  "Triumph Tiger 900 GT Pro, 2022–2023", "vLinker MC-Android", "vLinker MC-IOS",
  "android-v0.8.0", "2fdf4e70…1eb50ad6", "1234", ".", "Option", "Shift",
  "AltServer", "Xcode", "Signing & Capabilities", "Run", "Ko-fi",
].freeze

def page_path(page)
  page == "index.html" ? "/" : "/#{page}"
end

def localized_url(locale, page)
  segment = locale == "en" ? "" : "/#{locale}"
  "#{BASE_URL}#{segment}#{page_path(page)}"
end

def normalize(text)
  text.gsub(/\s+/, " ").strip
end

def translate_value(value, translations)
  translations.fetch(value, value)
end

def translate_json(value, translations, locale, page)
  case value
  when Hash
    value.transform_values { |child| translate_json(child, translations, locale, page) }
  when Array
    value.map { |child| translate_json(child, translations, locale, page) }
  when String
    if value == "en"
      locale
    elsif value == "#{BASE_URL}#{page_path(page)}"
      localized_url(locale, page)
    elsif value == "#{BASE_URL}/install-android.html"
      localized_url(locale, "install-android.html")
    elsif value == "#{BASE_URL}/install-ios.html"
      localized_url(locale, "install-ios.html")
    else
      translate_value(value, translations)
    end
  else
    value
  end
end

def alternate_links(document, page)
  document.css('link[rel="alternate"][hreflang]').remove
  canonical = document.at_css('link[rel="canonical"]')
  LOCALES.each_key do |locale|
    link = Nokogiri::XML::Node.new("link", document)
    link["rel"] = "alternate"
    link["hreflang"] = locale
    link["href"] = localized_url(locale, page)
    canonical.add_next_sibling(link)
    canonical = link
  end
  fallback = Nokogiri::XML::Node.new("link", document)
  fallback["rel"] = "alternate"
  fallback["hreflang"] = "x-default"
  fallback["href"] = localized_url("en", page)
  canonical.add_next_sibling(fallback)
end

def language_switcher(document, page, active_locale, label)
  document.css(".language-links").remove
  nav = document.at_css(".nav") or raise "Navigation missing in #{page}"
  container = Nokogiri::XML::Node.new("div", document)
  container["class"] = "language-links"
  container["aria-label"] = label
  LOCALES.each do |locale, config|
    link = Nokogiri::XML::Node.new("a", document)
    link["href"] = localized_url(locale, page)
    link["lang"] = locale
    link["hreflang"] = locale
    link["aria-current"] = "page" if locale == active_locale
    link.content = config.fetch(:label)
    container.add_child(link)
  end
  nav.add_child(container)
end

def translate_page(page, locale, config)
  translations = config.fetch("strings")
  source = File.read(File.join(DOCS, page))
  document = Nokogiri::HTML(source)
  document.at_css("html")["lang"] = locale
  document.css(".language-links").remove

  document.xpath('//text()[normalize-space()][not(ancestor::script)][not(ancestor::style)][not(ancestor::code)]').each do |node|
    original = normalize(node.text)
    next if PRESERVED_TEXT.include?(original)
    translated = translations[original]
    raise "Missing #{locale} translation in #{page}: #{original.inspect}" unless translated
    leading = node.text[/\A\s*/]
    trailing = node.text[/\s*\z/]
    node.content = "#{leading}#{translated}#{trailing}"
  end

  document.css("meta[content]").each do |node|
    node["content"] = translate_value(node["content"], translations)
  end
  document.css("[aria-label]").each do |node|
    node["aria-label"] = translate_value(node["aria-label"], translations)
  end
  document.css("img[alt]").each do |node|
    node["alt"] = translate_value(node["alt"], translations)
  end

  document.css('script[type="application/ld+json"]').each do |script|
    parsed = JSON.parse(script.text)
    script.content = "\n  #{JSON.pretty_generate(translate_json(parsed, translations, locale, page)).gsub("\n", "\n  ")}\n  "
  end

  canonical = document.at_css('link[rel="canonical"]')
  canonical["href"] = localized_url(locale, page)
  document.css('meta[property="og:url"]').each { |node| node["content"] = localized_url(locale, page) }

  document.css("[href], [src]").each do |node|
    %w[href src].each do |attribute|
      value = node[attribute]
      next unless value
      if value == "./"
        node[attribute] = "../"
      elsif value.start_with?("assets/") || %w[site.webmanifest llms.txt].include?(value)
        node[attribute] = "../#{value}"
      end
    end
  end

  alternate_links(document, page)
  language_switcher(document, page, locale, config.fetch("language_label"))

  output_dir = File.join(DOCS, locale)
  Dir.mkdir(output_dir) unless Dir.exist?(output_dir)
  File.write(File.join(output_dir, page), document.to_html)
end

LOCALES.each do |locale, locale_config|
  next if locale == "en"
  config = JSON.parse(File.read(File.join(DOCS, "translations", "#{locale}.json")))
  PAGES.each { |page| translate_page(page, locale, config) }
end

puts "Generated #{(LOCALES.length - 1) * PAGES.length} localized pages."
