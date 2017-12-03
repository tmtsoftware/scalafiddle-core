module Jekyll
  module ScalaFiddle
    class OptionsParser
      OPTIONS_SYNTAX     = %r!([^\s]+)\s*=\s*['"]+([^'"]+)['"]+!
      ALLOWED_FLAGS      = %w(
        autorun
      ).freeze
      ALLOWED_ATTRIBUTES = %w(
        template
        min_height
        layout
        theme
      ).freeze

      class << self
        def parse(raw_options)
          options = {
              :attributes => {},
              :filters    => {},
              :flags      => {}
          }
          raw_options.scan(OPTIONS_SYNTAX).each do |key, value|
            value = value.split(",") if value.include?(",")
            if ALLOWED_ATTRIBUTES.include?(key)
              options[:attributes][key.to_sym] = value
            else
              options[:filters][key] = value
            end
          end
          ALLOWED_FLAGS.each do |key|
            options[:flags][key.to_sym] = true if raw_options.include?(key)
          end
          options
        end
      end
    end

    class ScalaFiddleIntegration
      BODY_END_TAG = %r!</body>!

      class << self
        def append_scalafiddle_code(doc)
          @config = doc.site.config
          if doc.output =~ BODY_END_TAG
            # Insert code before body's end if this document has one.
            doc.output.gsub!(BODY_END_TAG, %(#{api_code(doc)}#{Regexp.last_match}))
          else
            doc.output.prepend(api_code(doc))
          end
        end

        private
        def load_template(template, dir)
          file = dir + "/" + template + ".scala"
          content = File.readlines(file)
          result = {
              :name => template,
              :pre => content.take_while {|l| !l.start_with?("////")},
              :post => content.drop_while {|l| !l.start_with?("////")}.drop(1)
          }
          if result[:post].empty?
            raise RuntimeException, "Template is missing a //// marker"
          end
          result
        end

        private
        def escape_string(strs)
          strs.join.gsub(/\\/, "\\\\\\\\").gsub(/\n/, "\\n").gsub(/\r/, "").gsub(/\t/, "\\t").gsub("'", "\\\\'")
        end

        private
        def api_code(page)
          result = ""
          if page.output =~ /<div data-scalafiddle=""/
            templates = page.output.scan(/<div data-scalafiddle="" data-template="([^"]+)"/).flatten
            unless templates.empty?
              result += <<HTML
<script>
  window.scalaFiddleTemplates = {
HTML
              dir = page.site.source + "/" + @config.fetch("scalafiddle", {}).fetch("templates", "_scalafiddle")
              js_code = templates.map {|template| load_template(template, dir)}
              result += js_code.map {|template|
                <<HTML
#{template[:name]}: {
      pre: '#{escape_string(template[:pre])}',
      post: '#{escape_string(template[:post])}'
    }
HTML
              }.join(",\n")
              result += <<HTML
  }
</script>
HTML
            end
            result += <<HTML
<script src='#{@config.fetch("scalafiddle", {}).fetch("url", "https://embed.scalafiddle.io")}/integration.js'></script>
HTML
          end
          result
        end

      end
    end

    class ScalaFiddleTag < Liquid::Block

      def initialize(_, args, _)
        @args = OptionsParser.parse(args)
        super
      end

      def render(context)
        site = context.registers[:site]
        converter = site.find_converter_instance(::Jekyll::Converters::Markdown)
        content = converter.convert(super(context))
        current_page = context.environments.first['page'] || {}
        result = <<HTML
<div #{render_attributes(current_page)}>#{content}</div>
HTML
        result
      end

      private
      def render_attributes(page)
        attributes = ["data-scalafiddle"]
        if @args[:attributes][:template]
          attributes << "data-template='#{@args[:attributes][:template]}'"
        end
        if @args[:attributes][:min_height]
          attributes << "data-minheight='#{@args[:attributes][:min_height]}'"
        end
        if @args[:attributes][:layout]
          attributes << "data-layout='#{@args[:attributes][:layout]}'"
        end
        if @args[:attributes][:theme]
          attributes << "data-theme='#{@args[:attributes][:theme]}'"
        end
        if @args[:flags][:autorun]
          attributes << "data-autorun"
        end
        attributes.join(" ")
      end

    end
  end
end

Liquid::Template.register_tag("scalafiddle", Jekyll::ScalaFiddle::ScalaFiddleTag)

Jekyll::Hooks.register [:pages, :documents], :post_render do |doc|
  Jekyll::ScalaFiddle::ScalaFiddleIntegration.append_scalafiddle_code(doc)
end

